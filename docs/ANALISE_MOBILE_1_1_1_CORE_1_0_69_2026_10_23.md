# OwnTV Mobile 1.1.1 — análise para a Mobile personalizada

Data: 23/10/2026. Estado: análise estática e plano; nenhuma implementação autorizada neste pedido. Não foram executados builds, testes de reprodução ou benchmarks. Este ficheiro é o único artefacto acrescentado ao projeto nesta análise.

Execução posterior, após autorizações separadas: [fase 1](IMPLEMENTACAO_MOBILE_1_1_1_FASE_1_2026_10_23.md), [fase 2](IMPLEMENTACAO_MOBILE_1_1_1_FASE_2_2026_10_23.md) e [fase 3](IMPLEMENTACAO_MOBILE_1_1_1_FASE_3_2026_10_23.md) implementadas e validadas automaticamente. A [subfase 4.1](IMPLEMENTACAO_MOBILE_1_1_1_SUBFASE_4_1_2026_10_23.md) foi depois implementada e validada automaticamente; as subfases 4.2–4.7 continuam pendentes; o texto da análise conserva o diagnóstico anterior à implementação. A implementação confirmou que o armazenamento automático local de software por servidor não tinha TTL, ao contrário dos 14 dias referidos no contexto upstream.

## Versões e método

- Mobile upstream: [v1.1.1](https://github.com/ahXN00/OwnTV_Mobile/releases/tag/v1.1.1), publicada em 21/10/2026; commit `cf70ad6dede8188febf4b440253074f8e7ebab98`.
- Comparação incremental Mobile: `v1.1.0..v1.1.1`, 22 ficheiros, 145 inserções e 52 eliminações. Parte é documentação/comentários; as alterações do motor estão no Core.
- Core upstream: [core-1.0.69](https://github.com/ahXN00/OwnTV_Core/releases/tag/core-1.0.69), commit `1de78b1`; comparado com `core-1.0.68`. Inclui muito código Sports, excluído da proposta.
- Binário: [libmpv 2026.10.4](https://github.com/ahXN00/OwnTV_libmpv/releases/tag/v2026.10.4), commit `f2b51ad0fa53ed21321b6a4bda7d236ecff37ef1`, comparado com `v2026.10.3`.
- Projeto inspecionado: `C:\Users\renat\Downloads\OwnTV_Mobile-main\OwnTV_Mobile-main`, HEAD `8e10ccc`, com alterações locais das integrações anteriores e Stage. O HEAD não descreve sozinho o código atual.
- Core efetivo: `vendor/OwnTV_Core` por `includeBuild`, schema privado **52**, Media3 **1.11.1**, libmpv **2026.10.3**. O número Maven 1.0.62 no catálogo da app é substituído pelo composite build; trocá-lo isoladamente não atualiza o motor efetivo.
- Upstream Core 1.0.69 conserva schema **49**. Nunca substituir o schema privado nem importar uma migração com o mesmo número e significado diferente.
- Consultados `ATUALIZACOES_OWNCORE_PENDENTES_DE_IMPLEMENTACAO.md`, os relatórios de 18/10 e o Stage de 19/10. Novas fontes obtidas apenas em diretórios temporários.

O resultado cruza os deltas de código com os consumidores locais. Evidência de código não demonstra a ocorrência numa instalação, nem o ganho de desempenho num telefone específico. Não houve acesso a streams/credenciais do utilizador.

## Decisão

Recomenda-se integração seletiva. As maiores novidades úteis são correções de sincronização/categorias, correções nativas HLS/HEVC/diferidos, menor trabalho JNI/diagnóstico e gestão mais explícita da superfície de vídeo. Há funcionalidades úteis de sincronismo por saída, mas precisam de regras próprias para backup, rotas de áudio e preferências existentes.

Preservar: canais/Guia/diferidos/gravações, Stage leve, modos simples, HLS estrito e exceções manuais, opções por canal, motor exclusivo, cancelamento HTTP confirmado, identidade da sintonia e ganho interno a 100%. TV e Core externo continuam fora do âmbito.

## 1. Atualização de listas: reduzir bloqueios prolongados da base de dados

**Confirmado no código local:** `UserDataResolver.purgeSnapshotOrphans()` faz um DELETE por elemento do snapshot, incluindo `content_order` e `custom_category_members`, dentro da transação. Os DAOs têm a organização por pasta/categoria; não existe um índice começado pela tripla consultada que resolva essas eliminações como pesquisas pontuais equivalentes às de favoritos.

**Delta upstream:** agrupar por tipo de operação/perfil/tipo de conteúdo, retirar IDs duplicados e eliminar grupos de até 900 IDs com `IN`, mantendo o filtro de órfãos. Reduz consultas e varrimentos repetidos sob o writer lock. Não elimina por si só todos os custos de um catálogo grande.

**Recomendação: prioridade alta, lote 1.** Portar apenas o delta dos dois DAOs e do resolver, conservando o remapeamento seguro de identidades e o âmbito do snapshot local. Não apagar membros válidos nem alargar a purga a outra fonte/perfil. Não exige, por si, migração de schema; os índices existentes merecem medição posterior se ainda houver lentidão.

**Aceitação:** snapshots com IDs repetidos, mais de 999 elementos e várias fontes/perfis; preservar membros existentes e personalização; comparar duração da sincronização sem publicar percentagens não medidas.

Fonte: [resolver upstream](https://github.com/ahXN00/OwnTV_Core/blob/core-1.0.69/core/src/main/java/tv/own/owntv/core/backup/UserDataResolver.kt).

## 2. A ordem das categorias de uma lista não deve apagar a de outra

**Confirmado localmente:** `CustomizationStore.setCategoryOrder()` substitui `categoryOrder` pela lista recebida. Se o ecrã mostra apenas uma fonte, a ordem guardada das restantes deixa de constar nesse valor.

**Delta upstream:** `mergeCategoryOrder(stored,onScreen)` integra as categorias visíveis na ordem anterior, conservando as outras. O modelo continua a ser uma ordem global combinada; não passa a haver um ranking completamente independente por cada fonte, especialmente para categorias personalizadas partilhadas.

**Recomendação: alta, lote 1.** Adaptar a fusão, usando chaves estáveis, evitando duplicados e testando a interação com categorias ocultas/partilhadas. Não alterar a preferência A–Z/fornecedor/personalizado da lista de canais.

**Aceitação:** ordenar A, ordenar B, voltar a A; categorias combinadas em duas listas; categorias ocultas e recém-adicionadas; backup/restauro da ordem.

Fonte: [CustomizationStore upstream](https://github.com/ahXN00/OwnTV_Core/blob/core-1.0.69/core/src/main/java/tv/own/owntv/core/customize/CustomizationStore.kt).

## 3. Edição de listas: menus por baixo da janela de edição

**Confirmado na estrutura local:** `EditPlaylistDialog` abre um `Dialog`, contém `AddSourceForm` e não fornece `MobileSheetHost` próprio. O formulário usa `MobileBottomSheet` para seletores, e esse componente entrega o conteúdo ao host herdado, que desenha na janela principal. O diálogo pode cobrir a folha e impedir a interação.

**Recomendação: alta, lote 1.** Host de folhas dentro do diálogo, como no upstream, ou edição na mesma janela com navegação adequada. A primeira opção é a mudança menor. Preservar password, PIN, teclado, cancelamento e valores existentes. Reproduzir o comportamento antes/depois no Android, pois a análise foi estrutural.

Fonte: [SettingsPlaylistsPage 1.1.1](https://github.com/ahXN00/OwnTV_Mobile/blob/v1.1.1/app/src/main/java/tv/own/owntv/mobile/ui/screens/settings/SettingsPlaylistsPage.kt).

## 4. libmpv 2026.10.4: atualização recomendada, com cinco correções relevantes

Os commits base de mpv e FFmpeg mantêm-se iguais aos da 2026.10.3. A novidade são patches mantidos pelo projeto, não uma mudança completa de versões dos motores.

| Correção | Valor para a nossa app | Condição de integração |
|---|---|---|
| HTTP chunked completo deixava-se classificar como resposta truncada | Evita repetir indefinidamente certos manifestos HLS no mpv até expirar o arranque | Preservar a reconexão de transferências realmente incompletas e o tratamento distinto de raw TS |
| HEVC sem extradata podia abortar o processo nativo | Evita um SIGABRT em determinados diferidos que começam a meio do stream | Testar HEVC direto/diferido e streams sem parâmetros iniciais; ausência de crash não garante que o fornecedor envie dados suficientes |
| MediaCodec recebe max-width/max-height | Corrige saída H.264 limitada a 30 fps observada pelo autor num TCL/Realtek | Resultado específico de hardware; testar SD/720p/1080p50/59.94 e HEVC HDR antes de generalizar |
| Opção wait_keyframe | Permite diferidos começados a meio de um GOP manterem hardware | Exige binário novo **e** opção do Core apenas em diferidos; limite de 300 pacotes, não um timeout fixo de 6 s |
| estimated-vf-fps por diferenças de timestamps | Dá ao AFR informação que faltava em streams sem FPS declarado | Só há benefício no ajuste do ecrã se o sistema/dispositivo aceitar o modo correspondente |

**Recomendação: alta, lote 2.** Atualizar a dependência no Core privado, validar as ABIs usadas e preservar o JNI/renderer de áudio local. Integrar a configuração de arquivo por item: `vd-lavc-o=wait_keyframe=1` para o diferido e limpar a opção no próximo direto. Não aplicar a todo o HLS. Não reconstruir toda a arquitetura do player só para mudar o binário.

Uma melhoria nativa mpv não altera automaticamente o percurso de vídeo ExoPlayer. As bibliotecas partilham infraestrutura FFmpeg para áudio, mas o HLS ExoPlayer continua a usar Media3.

Fontes: [changelog nativo](https://github.com/ahXN00/OwnTV_libmpv/blob/v2026.10.4/CHANGELOG.md), [patch HTTP](https://github.com/ahXN00/OwnTV_libmpv/blob/v2026.10.4/buildscripts/patches/ffmpeg/0003-http-a-finished-chunked-body-is-not-premature.patch), [patch HEVC](https://github.com/ahXN00/OwnTV_libmpv/blob/v2026.10.4/buildscripts/patches/ffmpeg/0005-mediacodec-hevc-without-extradata.patch), [patch keyframe](https://github.com/ahXN00/OwnTV_libmpv/blob/v2026.10.4/buildscripts/patches/ffmpeg/0006-mediacodec-wait-for-the-first-keyframe.patch).

## 5. Diferidos não devem ficar em software durante duas semanas após um incidente

**Confirmado localmente:** `PlaybackStartup` ainda instala a persistência de `ArchiveDecodeStore`; `LiveStreamQuirks` aprende por host. A preferência automática pode afetar todos os arquivos desse painel, em vez do único arranque problemático.

**Delta upstream:** elimina a persistência de 14 dias e o botão de esquecer essas correções. Ainda mantém uma aprendizagem por host durante o processo. Não é uma eliminação de toda a aprendizagem automática.

**Recomendação: alta, lote 2, adaptada.** Hardware continua primeira escolha quando o utilizador o pretende. Uma falha pode justificar uma recuperação finita para o pedido/canal atual; não perpetuar software automaticamente em todos os diferidos do mesmo servidor. Preservar as exceções manuais por canal e os resets de outras compatibilidades. Não remover menus/armazenamentos em bloco sem mapear os consumidores locais.

Com `wait_keyframe`, vários arquivos podem deixar de chegar ao ramo de resgate. Não retirar o resgate antes de testar arquivos que continuam danificados.

## 6. H.264 ExoPlayer: correção anunciada já coincide com o nosso padrão

O upstream retira `FLAG_DETECT_ACCESS_UNITS` da configuração global de HLS/TS, devido a congelações/perdas observadas com certos decoders. **A nossa Mobile já usa `DefaultHlsExtractorFactory(0,true)` e `HlsCompatibility.detectAccessUnits=false` por defeito.** A ativação é uma exceção explícita por canal.

**Decisão:** preservar o padrão local; não apresentar isto como uma correção nova nem remover uma exceção manual necessária. Num canal afetado, confirmar se a exceção foi ativada. Não escolher flags de parser apenas pela marca do aparelho.

Fontes: `vendor/OwnTV_Core/player-core/.../HlsCompatibility.kt`, `LivePreviewEngine.kt:3482,3503`; [LivePreviewEngine 1.0.69](https://github.com/ahXN00/OwnTV_Core/blob/core-1.0.69/player-core/src/main/java/tv/own/owntv/player/LivePreviewEngine.kt).

## 7. Superfícies e decoder: melhoria útil, mas não transplantar a espera inteira

**Delta upstream:** não iniciar um novo decoder Live sobre placeholder/detached surface; esperar a superfície visível; congelamento repetido escala de reconectar para novo decoder+superfície e depois outro motor. mpv passa o attach para a fila de comandos e usa uma geração para ignorar attaches já ultrapassados.

**Diferença local:** já há geração/identidade HTTP/superfície no Live Exo, confirmação de reprodução e cancelamento; o attach/detach nativo de `OwnTVPlayer` ainda pode executar JNI na thread principal. São áreas diferentes: libertar uma superfície não prova que a sessão do fornecedor fechou.

**Recomendação: lote 3.** Adaptar a espera pela superfície válida e a serialização nativa à identidade existente. Destruição de SurfaceView tem contrato de libertação: não basta enviar detach para outra thread e devolver sem garantia. Antecipar/ordenar o encerramento, descartar trabalho obsoleto e medir o tempo em falta.

**Não copiar sem revisão:** upstream tem `CountDownLatch.await` até **2500 ms na thread principal** no detach, recria a superfície após certos canais mpv ≤30,5 fps e adiciona até 30 pedidos de layout separados por 100 ms. São contornos motivados por dispositivos observados, não uma política universal ótima para smartphone. Uma medição anómala de FPS também não deve provocar reconstruções em todos os zappings.

**Aceitação:** A→B→C→A, preview/fullscreen/lista, rotação, home/retoma, mini-player, saída mpv/Exo, cancelamento durante attach. Sem som residual, callbacks antigos, janela preta nem pausa fixa em cada troca. Preservar HLS-only e motor exclusivo.

Fonte: [OwnTVPlayer 1.0.69](https://github.com/ahXN00/OwnTV_Core/blob/core-1.0.69/player-core/src/main/java/tv/own/owntv/player/OwnTVPlayer.kt), [VideoStage 1.1.1](https://github.com/ahXN00/OwnTV_Mobile/blob/v1.1.1/app/src/main/java/tv/own/owntv/mobile/ui/player/VideoStage.kt).

## 8. Erro terminal: parar só a reprodução realmente falhada

Upstream acrescenta `stop()` quando o Live apresenta erro terminal, para não deixar imagem/som por baixo do erro. O nosso `failLoad` tem uma defesa adicional: se a origem atual tem reprodução confirmada, descarta a falha e limpa a sobreposição. Esta defesa responde ao bug móvel anterior.

**Recomendação: lote 3.** Preservar a defesa de reprodução confirmada. Quando o erro é atual, terminal e verdadeiro, cancelar trabalho/libertar a origem e parar áudio/vídeo. Nunca pôr `stop()` incondicional no primeiro callback de erro, nem deixar uma falha antiga interromper o novo canal.

As novas classes upstream `LiveStartRungs`/`StartFailureLadder` extraem lógica para decisões testáveis. São sobretudo reorganização: não demonstram uma nova solução para o erro local de handover. Se adaptadas, as entradas devem incluir HLS estrito, exceção autorizada, motor exclusivo e orçamento finito. Copiá-las sem isso pode reabrir o fallback TS que o utilizador recusou.

## 9. Menos CPU/JNI e diagnóstico útil

**Novo valor:** observar cache mpv em segundos inteiros ou limitar a publicação para a UI; usar leitura exata apenas onde o watchdog precisa dela; compilar regex de ocultação de credenciais uma vez; agregar avisos iguais; contadores mpv esporádicos só com diagnóstico ativo. Não criar outro timer de saúde paralelo.

**Já melhor no local:** `DiagnosticWriter` tem fila limitada de 512 entradas, lotes, contadores de perdas/falhas e rotação com cauda limitada. O novo writer upstream usa executor agendado/buffer, mas não conserva essa fila limitada. Não o substituir integralmente.

**Recomendação: lote 3.** Portar apenas o delta que reduz trabalho, mantendo limites e sanitização. Coalescer a indicação de buffer sem retirar precisão às decisões de recuperação. Não fazer consultas JNI exatas por frame. Validar que relatórios continuam imediatos no ring e que uma falha de escrita nunca bloqueia o leitor.

## 10. FFmpeg de áudio: preservar os controlos locais e melhorar o Automático

**Estado local:** já existe FFmpeg ExoPlayer, fallback, preferência de áudio por software global/por canal, ordenação independente dos decoders de vídeo, mistura estéreo e limites de volume. Não é preciso voltar a acrescentar tudo como novidade.

**Mudança upstream:** FFmpeg passa a opção desligada por defeito e fixada no início do processo; `FfmpegAudio.takes()` recusa o formato quando existe um MediaCodec para o MIME. Isso evita o renderer FFmpeg ganhar só porque declara mais capacidades, mas a existência anunciada de um decoder não prova que ele funcione naquele aparelho.

**Recomendação: lote 4, adaptada.** Distinguir a preferência manual de software da seleção Automática. Em Automático, priorizar o decoder nativo saudável; no modo Software explícito, não deixar uma declaração de capacidades bloquear FFmpeg. Fallback após falha real tem de ser finito e preservar faixa/idioma. Se for acrescentado um interruptor mestre, não o restaurar como desligado silenciosamente sobre uma instalação dependente de MP2/software.

Também não vincular indiscriminadamente a deteção de áudio não reproduzível e a reação a mudanças de saída ao simples estado desse interruptor. O upstream faz parte desse acoplamento; o nosso comportamento de áudio precisa de continuar válido com hardware e software.

Aplicar atributos de uso de média/conteúdo de vídeo coerentes aos ExoPlayers é um delta pequeno útil; conservar o proprietário atual do foco áudio para não pedir foco duas vezes. Isso não é garantia de corrigir todas as boxes sem som.

Fonte: [ExoAudioGate upstream](https://github.com/ahXN00/OwnTV_Core/blob/core-1.0.69/player-core/src/main/java/tv/own/owntv/player/ExoAudioGate.kt).

## 11. Atraso de áudio por saída: útil para telefone/tablet

Upstream soma um ajuste por classe de saída ao atraso base efetivo (global, por canal ou guardado para o item). Bluetooth, altifalante, HDMI/ARC e outros podem ter compensações diferentes. **Não altera o volume nem cria áudio ausente.**

**Recomendação: lote 4, opcional por defeito em 0 ms.** Mostrar a saída usada e o total efetivo; documentar o sinal do atraso; respeitar a escolha por canal. Não introduzir botões de áudio no leitor que o utilizador removeu: configuração em Definições.

**Revisão necessária:** antes do Android 13, o upstream deduz a saída da lista de equipamentos ligados; mesmo no Android 13+, só callbacks de ligação/desligação não cobrem todas as alterações de rota. Um dispositivo conectado não é necessariamente o destino em uso. Revalidar na retoma/início da reprodução e acompanhar eventos de rota disponíveis sem polling contínuo.

**Backup:** upstream exclui explicitamente `audio_delay_by_output` por ser específico do dispositivo e guarda o interruptor FFmpeg fora do backup. O utilizador pediu backups completos; não herdar a exclusão sem uma política clara. Proposta: guardar calibração numa secção de compatibilidade do dispositivo, aplicar em restauro para o mesmo aparelho quando solicitado e deixar desmarcada ao transferir para outro. Conservar importação compatível com ficheiros antigos e não transportar identificadores sensíveis de hardware.

Fontes: [AudioOutputDelay](https://github.com/ahXN00/OwnTV_Core/blob/core-1.0.69/core/src/main/java/tv/own/owntv/core/player/AudioOutputDelay.kt), [teste de cobertura do backup](https://github.com/ahXN00/OwnTV_Core/blob/core-1.0.69/core/src/test/java/tv/own/owntv/core/settings/SettingsBackupCoverageTest.kt), [API AudioManager](https://developer.android.com/reference/android/media/AudioManager#getAudioDevicesForAttributes(android.media.AudioAttributes)).

## 12. Preferir SDR e AFR exato: úteis em condições específicas

**SDR:** a nova preferência filtra variantes HDR de um manifesto HLS que também oferece SDR, no ExoPlayer. Não converte HDR-only em SDR nem afeta todas as ligações mpv. Na implementação upstream, a ausência de VIDEO-RANGE é tratada como não HDR; não afirmar capacidade de identificar qualquer variante. A leitura integral e duplicada dos manifestos para filtragem merece limite de tamanho e cuidado nas atualizações periódicas.

Proposta: opção avançada desligada, idealmente com exceção por canal. Preservar headers, chaves/DRM, grupos de áudio e qualidades. O antigo `hdr_enabled=false` não tem significado idêntico a `prefer_sdr=true`; definir migração explícita e manter a intenção do utilizador.

**AFR:** `PtsFrameRate` resolve FPS por timestamps em vez de arredondar contadores do relógio, permitindo distinguir 59,94 de 60, por exemplo. O patch mpv também dá a estimativa quando falta FPS declarado. Útil no direto e nos diferidos mesmo sem filmes/séries.

Proposta: melhorar medição quando há consumidor (AFR/estatísticas), com janela limitada, geração do item e sem acumular amostras antigas. AFR mantém opção atual; a plataforma pode recusar a frequência e alguns painéis mostram um intervalo preto ao mudar. Não ligar universalmente em todos os smartphones.

Fontes: [SdrVariants](https://github.com/ahXN00/OwnTV_Core/blob/core-1.0.69/player-core/src/main/java/tv/own/owntv/player/SdrVariants.kt), [PtsFrameRate](https://github.com/ahXN00/OwnTV_Core/blob/core-1.0.69/player-core/src/main/java/tv/own/owntv/player/PtsFrameRate.kt), [orientação Android sobre FPS](https://developer.android.com/media/optimize/performance/frame-rate).

## 13. Quedas de frames: sinalizar e recuperar sem mudar decisões do utilizador

Upstream deteta casos extremos (mais de 100 perdas e mais perdas que frames apresentados numa janela de cerca de 10 s) para abandonar Exo; um aviso menos severo usa cerca de 30 s com mais de 20% e pelo menos 150 perdas. Os limiares foram escolhidos para casos observados, não são uma norma Android.

Proposta: usar contadores comparáveis de frames realmente apresentados/perdidos, reiniciar no novo decoder e não misturar callbacks de sintonias. Avisar uma vez, sem spam, com armazenamento de avisos limitado. Em Automático, recuperação finita pode experimentar mpv mantendo HLS; em Exo exclusivo, não trocar o motor à revelia do utilizador. Quedas fornecidas pela origem não se resolvem mudando decoder.

Sem ensaio, não promover uma indicação de suavidade a prova de falha de rede ou de descodificação. Esta funcionalidade tem prioridade inferior às correções de dados e nativas.

## 14. O que não copiar ou não repetir

- Sports/calendários/equipas/alertas, filmes/séries e a navegação upstream completa.
- Stage completo com Glass/efeitos por defeito: o Stage local já foi adaptado para canais e é mais simples no percurso normal.
- Correção de scroll da biblioteca de filmes: rota excluída do produto. A ideia de conservar posição no regresso merece ensaio local dos canais, mas não copiar o ficheiro de biblioteca nem prometer que corrige o foco no Live.
- Fix RTL `placeRelative`: upstream usa um `ShellLayout` próprio; local usa Row/Scaffold/NavigationRail. Não existe a mesma colocação absoluta para trocar diretamente. Testar RTL se necessário, adaptar apenas falha reproduzida.
- Hardware decoding aplicado no próximo tune: local já guarda `builtForHwDecoding` e compara no play. Não duplicar essa solução.
- Logging em thread secundária: já existe e tem limites mais fortes no local. Portar só melhorias incrementais.
- Timeshift local após standby: correção válida no subsistema upstream, mas não encontrei consumidor ativo de `TimeshiftManager` na app Mobile atual. Não importar servidor/cache local para justificar o patch; diferidos do fornecedor continuam outra funcionalidade.
- Alterar a retoma após falha para esperar 2 s em todos os canais: o local usa retoma imediata quando há dados reproduzíveis, requisito expresso. Não copiar os defaults upstream de `MpvLoadOptions` nem repor pre-buffer após underrun.
- Esperas fixas no zapping, troca automática de HLS para TS ou perda do motor exclusivo.
- Volume até 150%, novos controlos de áudio retirados, meteorologia, PiP e OpenSubtitles.
- Trocar Core/schema completos ou assumir que o bump Maven basta para atualizar o Core privado.

## Plano de implementação proposto

| Lote | Âmbito concreto | Evidência necessária para concluir |
|---|---|---|
| 1 — Listas e interface | DELETEs por lotes; fusão da ordem de categorias; host do diálogo de edição | Testes de dados por fonte/perfil e limites SQLite; edição real com teclado/seletores; sem alterações de reprodução |
| 2 — Binário e diferidos | libmpv 2026.10.4; wait_keyframe só em arquivo; retirar penalização automática persistida por host | Compilação/contrato JNI nas ABIs; HLS chunked; HEVC sem extradata; arquivos hardware; zero regressões nos diretos saudáveis |
| 3 — Player e custo de execução | Superfícies/filas adaptadas; erro terminal com guardas; precisão funcional e coalescência UI; regex/logs | Zapping repetido, rotação/retoma, callback antigo, erro falso vs terminal; medir thread principal/CPU e manter cancelamento HTTP |
| 4 — Opções avançadas | Áudio Automático/Software coerente; atraso por saída com backup; AFR preciso; SDR opcional; diagnóstico de perdas | Matriz AAC/MP2/AC3 e rotas; motor exclusivo; opções por canal; restore; AFR ligado/desligado; HLS misto e HDR-only |

### Divisão autorizada da fase 4

| Subfase | Âmbito | Estado |
|---|---|---|
| 4.1 | Áudio Automático/Software, recuperação finita e retenção | Implementada; [relatório](IMPLEMENTACAO_MOBILE_1_1_1_SUBFASE_4_1_2026_10_23.md) |
| 4.2 | Identificar a saída de áudio efetivamente usada | Implementada; validação física pendente. [Relatório](IMPLEMENTACAO_MOBILE_1_1_1_SUBFASES_4_2_A_4_7_2026_10_23.md) |
| 4.3 | Sincronismo por saída, definições e backup | Implementada; validação física pendente. [Relatório](IMPLEMENTACAO_MOBILE_1_1_1_SUBFASES_4_2_A_4_7_2026_10_23.md) |
| 4.4 | Medição de FPS e AFR preciso | Implementada; validação física pendente. [Relatório](IMPLEMENTACAO_MOBILE_1_1_1_SUBFASES_4_2_A_4_7_2026_10_23.md) |
| 4.5 | Preferir SDR, opcional e por canal | Implementada; validação física pendente. [Relatório](IMPLEMENTACAO_MOBILE_1_1_1_SUBFASES_4_2_A_4_7_2026_10_23.md) |
| 4.6 | Diagnóstico de perdas, sem recuperação automática nova | Implementada; validação física pendente. [Relatório](IMPLEMENTACAO_MOBILE_1_1_1_SUBFASES_4_2_A_4_7_2026_10_23.md) |
| 4.7 | Recuperação limitada de degradação grave | Implementada; validação física pendente. [Relatório](IMPLEMENTACAO_MOBILE_1_1_1_SUBFASES_4_2_A_4_7_2026_10_23.md) |

A autorização de 23/10/2026 abrangeu todas as subfases restantes, agora integradas e validadas automaticamente; aceitação física pendente. As novas configurações persistentes devem incluir backup na mesma subfase.

Cada lote deve partir de snapshot verificável do checkout atual, sem reset aos trabalhos anteriores. Validar um lote antes do seguinte; as correções nativas devem ter ensaio próprio para permitir identificar regressões. Testes unitários/compilação/lint são necessários após implementação, mas não substituem reprodução, toque, armazenamento, rota de áudio ou compatibilidade física.

O critério final inclui: canais saudáveis mantêm o arranque, buffer e comandos atuais; A→B→C→A não deixa pedidos/sessões locais antigos a interferir; o novo canal não é interrompido por callback anterior; diferidos mantêm horários/posição e escolha de motor; nenhuma credencial aparece no diagnóstico; volume interno continua 100%.

Esta análise não demonstra que a v1.1.1 elimina o bloqueio histórico de handover do fornecedor. As alterações de superfície, de parser e de HTTP nativo atuam em causas diferentes. A confirmação exige APK e ensaio comparável com os registos da origem atual.
