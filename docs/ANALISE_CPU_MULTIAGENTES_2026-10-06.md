# OwnTV Mobile — auditoria de CPU e plano de otimização

Data: 06/10/2026. Estado: análise; nenhuma implementação aprovada neste pedido.

## Âmbito e evidência

Análise estática do projeto `OwnTV_Mobile-main/OwnTV_Mobile-main` e do respetivo `vendor/OwnTV_Core` independente. Três agentes especializados: interface Compose; dados/EPG/persistência; reprodução/áudio/vídeo. A revisão cruzada entre os agentes e a raiz estabeleceu as prioridades abaixo. A OwnTV de TV e o Core partilhado não foram modificados. Este relatório é o único artefacto novo da análise.

Os caminhos indicados são relativos à raiz do projeto mobile. A existência dos mecanismos foi verificada no código. Não foram medidos percentagens de CPU, energia, temperaturas, tempos de consulta ou frames em dispositivo. Recomposição elegível não significa que todo o ecrã seja redesenhado: Compose pode saltar descendentes. Os ganhos apresentados são esperados, não resultados de benchmarks.

## Decisão do conselho

Reduzir operações e despertares antes de introduzir mais paralelismo. Primeiro consultas redundantes, coletores sem interface e observação de tempo; depois configurações e Paging; em seguida gravações e matching; efeitos visuais apenas quando ativados. Medir CPU total e fluidez separadamente. Não alterar globalmente HLS estrito, buffers, descodificação, teardown de stream ou watchdogs para resolver custos de interface.

## Lote 1 — trabalho desnecessário ativo e correções de EPG

### CPU-01 — consulta EPG acompanha o cancelamento da lista

Evidência: `app/src/main/java/tv/own/owntv/mobile/ui/screens/live/LiveScreen.kt:154–160` e `LiveViewModel.kt:439–444`.

`collectLatest` cancela a espera configurada, mas `loadNowPlaying` lança outro trabalho em `viewModelScope`. A consulta iniciada deixa de pertencer ao trabalho da lista; batches podem sobrepor-se antes da resposta. Tornar a operação suspensa/cancelável sob controlo do coletor, juntar pedidos equivalentes e validar geração/contexto antes de publicar. Manter wrapper separado apenas se outro chamador precisar.

Validação: contagem de consultas durante scroll, mudança de categoria/perfil e saída do ecrã; não publicar resultados antigos. Manter a opção 0/3 segundos existente.

### CPU-02 — cache exterior de títulos expira e guarda ausência temporária

Evidência: `LiveViewModel.kt:235–238,439–444`.

O mapa exterior `Map<Long,String>` não guarda fim do programa. Canais respondidos são excluídos de futuras leituras; canais sem resposta podem voltar a ser consultados. O cache interno do `LiveEpgReader` não resolve esse mapa exterior. Guardar título opcional, expiração e revisão do contexto; expirar no fim do programa, TTL curto para ausência, invalidar por perfil/fontes/EPG/correção horária. Atualizar apenas canais visíveis.

Validação: fake clock atravessa fim de programa; EPG inicialmente vazio passa a existir; revisão/offset durante consulta. Não criar requisições de rede por canal: a leitura em lotes já existe.

### CPU-03 — retirar coletores sem consumidor visível

Evidência: `LiveScreen.kt:116,215,281`; `GuideScreen.kt:90,168`; `ui/player/PlayerScreen.kt:133,229–236,645–653`; `PlayerControls.kt` oculta favoritos/canal anterior.

A interface removida continua a observar favoritos/canal anterior e transformar resultados. Retirar subscrições e parâmetros mortos destes caminhos; preservar dados e backup, bem como qualquer utilização realmente existente noutro ecrã. Rever também feeds sem destino visível antes de eliminar tarefas.

Validação: grafo de consumidores, contagem SQL/Flow e ausência de regressões nas funções mantidas. Não apagar tabelas ou dados.

### CPU-04 — isolar os ticks de diferidos do fullscreen

Evidência: `ui/player/PlayerScreen.kt:137–138,610` e `vendor/OwnTV_Core/core/src/main/java/tv/own/owntv/core/live/LiveTimeshift.kt:198–234`.

`offsetSec` e `watchingWallMs` são lidos no nó raiz e emitidos durante diferido. Observar apenas no relógio/timeline montados com o HUD visível, passando State/Flow sem leitura superior. A lógica de timeshift continua a funcionar.

Validação: recomposições raiz com HUD visível/oculto, seek, regresso ao direto e cálculo de posição corretos. Não suspender o tick funcional de LiveTimeshift.

### CPU-05 — relógios visuais respeitam visibilidade/lifecycle

Evidência: `ui/components/ClockTick.kt:25–31`; `GuideScreen.kt:283–295`.

Um `LaunchedEffect` pode continuar quando a Activity está parada e a composição permanece. Vincular ticks ao lifecycle STARTED/visibilidade e ler hora imediatamente na retoma. O relógio já é de 30 segundos: o custo por tick é provavelmente pequeno. Não pausar reprodução, gravação ou jobs necessários por este mecanismo.

## Lote 2 — configurações, listas e sessão multimédia

### CPU-06 — evitar parsing JSON por alterações irrelevantes

Evidência: `vendor/OwnTV_Core/core/src/main/java/tv/own/owntv/core/settings/SettingsRepository.kt:298–300,1419–1420`; `core/customize/CustomizationStore.kt:177–180`.

`map(parse).distinctUntilChanged()` evita propagar valores iguais, mas executa o parsing antes de comparar. Para JSON pesado: projetar a string/tuplo de strings, aplicar distinct, depois descodificar; partilhar resultado quando há consumidores simultâneos e definir lifecycle/limite. Não alterar todos os flows escalares, que já têm distinção.

Validação: spy do decoder; alterar preferência sem relação não volta a analisar configurações por canal. Atualizações relevantes e backup/restauro devem continuar corretos.

### CPU-07 — não reconstruir o Pager por qualquer personalização

Evidência: `LiveViewModel.kt:246–250`.

O objeto completo de personalizações entra na chave de criação do Pager. Projetar uma configuração estrutural mínima e aplicar distinct antes do Pager. Separar apresentação/EPG quando não mudam query ou agrupamento.

Validação: alterações de EPG/recuperação não recriam listas desnecessariamente. Ocultação, ordem, categorias movidas e grupos manuais devem continuar a invalidar quando alteram as linhas. Preservar posição/seleção.

### CPU-08 — carregar permissões/categorias das versões em lotes

Evidência: `LiveViewModel.kt:366–398`.

A pesquisa obtém até 2 000 candidatos e a verificação repete chamadas DAO/perfil/categoria por candidato. Criar snapshot de permissões/fontes/categorias, resolver IDs manuais em batches e filtrar em memória. Um índice limitado por fonte/revisão pode ser reutilizado.

Validação: resultados/ordem idênticos, categorias ocultas, bloqueios adultos e mudança de perfil; consultas proporcionais aos lotes, não aos candidatos. Não remover restrições para ganhar velocidade.

### CPU-09 — publicar MediaSession quando o estado muda

Evidência: `vendor/OwnTV_Core/player-core/src/main/java/tv/own/owntv/player/PlaybackSession.kt:90–99,154–175`.

A posição participa no estado publicado repetidamente. Em direto, comparar playing/buffering/error/request/ações e publicar mudanças imediatamente, sem repetir por posição irrelevante. Em diferido, ancorar posição/velocidade/tempo e publicar seek/pause/retoma; testar necessidade de sincronização periódica. Metadata já tem diff.

Validação: lockscreen, notificações, Bluetooth, audio focus, seek e recuperação. Não usar intervalo universal sem teste e não remover a sessão multimédia.

### CPU-10 — diagnóstico sem despertares desnecessários e atribuição correta

Evidência: `player/LivePreviewEngine.kt:3607–3624`; `player/PlayerDiagnostics.kt:26–57`.

O sampler desperta a cada 2 segundos por instância mesmo desligado, verificando depois a flag. Executar apenas com diagnóstico ativo e tentativa/stream relevante; tentativa presa tem de continuar observável. O leitor logcat é singleton, mas não tem stop explícito nem filtro PID no comando. Acrescentar start/stop seguro por utilizadores ativos, filtro PID quando suportado e fallback documentado; encerrar subprocesso em finally.

Validação: diagnóstico OFF sem wakeups do sampler; ON regista tentativa bloqueada. Erros de outro processo não contaminam recuperação. Não desligar diagnóstico essencial de codecs só porque o registo detalhado está OFF. Não alterar watchdogs.

## Lote 3 — gravações, associação e custos localizados

### CPU-11 — SHA-256 durante a transferência do segmento

Evidência: `core/recording/HlsSegmentTransfer.kt:10–33`; `HlsCaptureSession.kt:97–110,129–135`.

Após gravar o segmento, o commit relê integralmente para calcular SHA-256. Calcular digest durante a cópia e transportar recibo tipado até commit pode eliminar a releitura. Manter tamanho/EOF/container, fsync, publicação atómica e journal. O ficheiro não pode mudar entre transferência e commit; recuperação após crash mantém verificações.

Validação: hash igual, truncamento/cancelamento/falha escrita/crash/restart e bytes de disco. Esta mudança afeta gravação/download HLS, não reprodução normal.

### CPU-12 — transação única e progresso visual limitado nas gravações

Evidência: `core/recording/RecordingEngine.kt:651,746–756`.

Capture/progresso são escritos separadamente por segmento e podem gerar estados intermédios para observadores. Primeiro unir numa transação e limitar apenas publicação visual, mantendo evento final. Não reduzir persistência durável sem prova de reconstrução pelo journal e quotas corretas.

Validação: observadores/SQL por minuto, pause/finalização/crash e orçamento de disco.

### CPU-13 — índice preparado para associação EPG e concorrência controlada

Evidência: `core/epg/EpgMatcher.kt:301–329,410–436`; `GuideScreen.kt:206`; `GuideViewModel.kt:734`; `GuideCandidates.kt:112`.

O matcher bulk e picker continuam acessíveis no mobile. A paralelização por núcleos reduz espera, mas não o total de operações. Considerar correspondências exatas/tokens e normalização preparada, preservando scoring/tie-break. Concorrência menor durante playback é uma hipótese a medir. Rever a presença do botão autoassociação em separado, pois a intenção anterior era ocultá-lo.

Validação: mesmo conjunto e ordem de resultados, português/acentos, nomes parecidos; CPU total e latência com/sem reprodução. Não eliminar candidatos por heurística sem equivalência/testes.

### CPU-14 — metadados de grupos fora do desenho das linhas

Evidência: `LiveScreen.kt:208–211`; `ChannelVersionPolicy.kt:10–14`; `ChannelAlternatives.kt:20–25`.

A comparação do canal atual repete normalização/regex ao avaliar linhas. Memorizar identidade de grupo por nome original/fonte/revisão de grupos, ou criar modelo de linha fora da composição. Invalidar quando há alterações relevantes e limitar cache. Medir antes de intervenção ampla; custo por linha pode ser moderado.

### CPU-15 — reduzir chamadas de volume/Cast localizadas

Evidência: `PlayerScreen.kt:514–526,957–972`; `CastStage.kt:77–78,163`.

Memorizar helper do gesto de volume e escrever apenas mudanças efetivas, mantendo o volume interno do engine a 100% e reagindo às teclas externas. Cast pode observar posição dentro de CastProgress em vez da raiz. Testar gestos/teclas e seek Cast; ganhos locais ainda não medidos.

## Lote 4 — efeitos opcionais e perfis de execução

### CPU-16 — separar geometria estática e posição dinâmica do vidro

Evidência: `ui/theme/MobileGlass.kt:570–630`.

Offset/janela mudam durante scroll e invalidam drawWithCache, reconstruindo Path/Outline/brushes. Cache estático de geometria/tinta; offsets no desenho. Vidro está OFF por predefinição e a política low-RAM/hardware blur já existe: não priorizar como problema universal. Medir CPU e RenderThread/GPU separadamente.

### CPU-17 — animação de chegada observada no desenho

Evidência: `MobileGlass.kt:575,671–672,698–706`.

Progresso observado na composição durante animação de 420 ms por superfície. Passar State/Animatable e ler no draw; avaliar retirar animação de entrada das linhas durante scroll. Não é animação infinita e respeita efeito desligado.

### CPU-18 — atualizar a jornada de benchmark/baseline existente

Evidência: `baselineprofile/src/main/java/tv/own/owntv/mobile/baselineprofile/BaselineProfileGenerator.kt`; `app/src/main/generated/baselineProfiles/`.

Já existe gerador e perfis. A jornada usa coordenadas/NAV_SLOTS=5 e descreve destinos que foram simplificados. Verificar destinos reais com seletores estáveis, cobrir lista/versões/guia/diferido e regenerar em dispositivo configurado. Não criar um segundo sistema nem afirmar ganhos sem comparação. Perfil AOT não elimina trabalho desnecessário.

## O que preservar

- Paging, chaves estáveis, consultas EPG em lotes, parser streaming/cancelável e transações/hash limitados existentes.
- HUD escondido retirado por AnimatedVisibility e posição VOD isolada: já são boas escolhas.
- Heartbeat único de 1 Hz/WhileSubscribed, liveSeekWindow dependente de subscribers e escrita assíncrona limitada de diagnóstico.
- Retorno rápido do throughput quando medição desligada e diff de metadata da MediaSession.
- Ticks necessários a resume/EOF/seek/watchdogs e contador atómico de frames: não há evidência para remover.
- Conversão PCM necessária e fallback de áudio para caixas incompatíveis; não forçar software/hardware globalmente.
- Estrutura de backup, perfis, grupos/canais ocultos, correção independente dos pedidos diferidos e volume interno a 100%.

## Medição e critérios de aceitação

Usar release/profileable em dispositivo físico, mesmo conteúdo/lista, temperatura semelhante e mesmas definições. A chave de debug usada para assinar o release não transforma o build em debug; isDebuggable/otimizações são parâmetros distintos.

Cenários separados: lista sem vídeo; scroll e pesquisa; versões; fullscreen direto HUD escondido/visível; diferido; zapping A/B/C/A; background sem playback/jobs; áudio em background quando autorizado; sync EPG parado/ativo; gravação e download com/sem reprodução; vidro OFF/ON.

Métricas: CPU time por processo/thread numa janela fixa, wakeups, consultas SQL, parsing JSON, criações de PagingSource, allocations/GC, bytes de disco, recomposições e frame timing p95/p99. Distinguir CPU total de fluidez da main thread. Validar também primeiro frame, zapping, áudio, lockscreen e integridade de EPG/backup/gravações.

Critérios: operações redundantes e wakeups removidos conforme contadores; ausência de resultados de contexto antigo; menores custos medidos nos cenários afetados; nenhuma regressão material no arranque/zapping/áudio/frame timing nem perda de funcionalidades. Não prometer 0% CPU, percentagens ou melhoria em todos os dispositivos.

## Fontes primárias

- Android: https://developer.android.com/kotlin/coroutines/coroutines-best-practices
- Compose: https://developer.android.com/develop/ui/compose/performance/phases
- Macrobenchmark: https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview
- Baseline Profiles: https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile
