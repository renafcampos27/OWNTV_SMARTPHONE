# Plano de adaptação App Channel TV → Mobile

Data: 15/10/2026. Estado: **análise e plano; sem implementação**.

Atualização de 16/10/2026: os **lotes 1 e 2 foram implementados** na Mobile/Core privado. Ver [registo de implementação e validação](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/docs/IMPLEMENTACAO_ADAPTACAO_LOTES_1_2_2026_10_16.md). A descrição da análise de 15/10 abaixo é histórica; os lotes 3–7 continuam pendentes.

## 1. Resultado e âmbito

Há correções e funcionalidades úteis na TV para adaptar à aplicação dedicada a smartphone/tablet. A recomendação é uma portagem seletiva, por contratos e testes. Substituir o Core ou os ecrãs da Mobile apagaria melhorias próprias de reprodução, SAF, EPG e ciclo de vida.

Participaram três agentes especializados — reprodução/HLS/áudio; dados/EPG/gravações; interface/Compose/tablet — com síntese crítica do coordenador e revisão cruzada das prioridades e dependências. Os três voltaram a rever os lotes finais das respetivas áreas. Foram incorporadas as ressalvas sobre autenticação GCM, acessibilidade e ausência de obrigação de arrastar. As diferenças foram verificadas no código atual; os documentos anteriores serviram de orientação, não de prova de implementação.

Este trabalho não altera código, preferências, dependências, TV nem Core externo. Não gera APK, commit ou publicação. Foi criado apenas este plano no projeto Mobile.

### Projetos comparados

- TV: [OwnTV-main](C:/Users/renat/Downloads/OwnTV-main).
- Core externo da TV: [OwnTV_Core](C:/Users/renat/Downloads/OwnTV_Core).
- Mobile: [OwnTV_Mobile-main](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main).
- Core privado Mobile: [vendor/OwnTV_Core](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/vendor/OwnTV_Core).

### Condição importante do doador

A TV aplica [custom-core.settings.gradle.kts](C:/Users/renat/Downloads/OwnTV-main/gradle/custom-core.settings.gradle.kts) e fixa um ZIP em [core-source-lock.json](C:/Users/renat/Downloads/OwnTV-main/core-source-lock.json). O SHA-256 do arquivo foi confirmado: `7d575983924a02912495339bc8b31faf2ba1f4c00ae7aa989ee0d2be5b3367a6`, 989 ficheiros. A etiqueta personalizada deste lock não equivale a uma versão upstream integral.

O override global aponta ao Core externo. Na comparação com o ZIP, **quatro ficheiros externos diferem**: BackupManager, PlaybackDatabaseBackup, HttpClient e SettingsRepository. O verificador da TV recusaria essas diferenças enquanto os inputs não fossem reconciliados. Não foi executado build da TV nem corrigido esse projeto. Alterações recentes nestes quatro ficheiros são candidatas a validar, não soluções já consolidadas no snapshot.

Nos caminhos `core/src` e `player-core/src` do ZIP, compararam-se 903 entradas: 619 iguais à Mobile, 175 diferentes e 109 ausentes na Mobile, incluindo testes e recursos. Há 184 diferenças em `src/main`. Esta contagem identifica divergências de ficheiros; não significa 184 correções em falta nem prova que a TV seja melhor em todos esses pontos.

## 2. O que a Mobile já tem e deve conservar

| Área | Situação atual | Decisão |
|---|---|---|
| Reprodução e lifecycle | Lote A de 15/10: intenção explícita na pausa/retoma, revisões de item/comando/áudio, continuação Exo→mpv cancelável e eventos Exo associados ao pedido. | Manter; toda adaptação do player deve passar pelos testes destes contratos. |
| Backup e SAF | Lote B de 15/10: parâmetros recuperáveis, palavra-passe só em memória, temporários por operação, cancelamento e confirmação de eliminação. | Completar conteúdo/validação do backup, sem importar a UI/servidor de backup TV. |
| Core/libmpv | Core privado com integração seletiva até às recomendações anteriores de Core 1.0.66/libmpv 2026.10.3. | Sem nova troca de binários ou substituição integral neste plano. |
| Telefone/tablet | TwoPane em largura expandida, navegação adaptativa, Guia com apresentação distinta por largura e bottom sheets adequados a landscape. | Corrigir casos concretos; não apresentar “suporte tablet” como funcionalidade inexistente. |
| Configuração por canal | Rascunho, Guardar/Cancelar e origem canal/lista/geral já existem. | Reforçar atomicidade/contexto, sem duplicar opções. |
| Atualizações da app | Pesquisa manual e controlo automático já existem. | Melhorar escolha e validação do APK, sem acrescentar outro botão igual. |
| Lista | Ordenação, indicador do canal atual e EPG de itens visíveis em lote já existem. | Preservar; avaliar apenas a âncora de regresso e estados de erro. |
| EPG privado | Locks por store, fila de preenchimento, fingerprints, spool com limite e metadados ricos. | Não substituir EpgRepository/spool completos pelos da TV. |
| Rede HLS | Cancelamento HTTP com confirmação local, fecho de corpos e pedidos tardios já existe. | Não vender esse mecanismo como novidade nem aumentar atrasos globalmente. |
| Áudio | Volume interno unitário/100%; ganho fica no sistema. | Manter em todos os motores e modos. |

Referências de continuidade: [Lote A](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/docs/LOTE_A_ESTADO_REPRODUCAO_2026_10_15.md), [Lote B](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/docs/LOTE_B_BACKUP_EXPORTACAO_2026_10_15.md).

## 3. Decisão do council e ordem

A revisão rejeitou a cópia integral da TV. A proposta de UX coloca estados e modelo do Guia antes do cosmético; dados recomenda antecipar os defeitos que podem produzir ficheiros incompletos ou perder índices. O coordenador adota esta ordem:

1. **Integridade, privacidade, backup e perfis.**
2. **Integridade e disponibilidade das gravações.**
3. **Guia correto e com menos trabalho invisível — continuação do antigo Lote C.**
4. **Correções pequenas e isoladas de reprodução/áudio.**
5. **Diferidos, resolução de fontes e identidade dos pedidos.**
6. **Versões, definições, importação e atualizador, em sublotes independentes.**
7. **Medição e limpeza verificável.**

A recolha de baseline precede qualquer otimização de desempenho nos lotes 3/7; não é necessário esperar pelo último lote para medir. As fases são entregas independentes: sem misturar uma mudança de interface com uma reescrita do player.

P1 abaixo significa prioridade por consequência e evidência estática, não frequência comprovada no aparelho. P2 significa melhoria funcional/desempenho ou robustez com risco menor. Itens condicionados precisam de prova específica antes da implementação.

## 4. Lote 1 — integridade, backup, privacidade e perfis

### I1 — importação FTS não pode deixar índices B-tree removidos — P1

**Diferença:** na Mobile, BulkInsertHelper remove índices sem transmitir `ftsOnly`, mas a restauração nesse modo evita repor os B-tree. Há chamadas reais nos syncers Xtream/M3U/Stalker. A TV protege a remoção.

**Solução:** portar apenas a guarda e o contrato de restauração/cancelamento, mantendo coordenação de escritores. Sem migração nem aumento de threads.

**Aceitação:** SQLite real antes/depois de importação, erro e cancelamento; FTS-only conserva B-tree; importação completa restaura índices; escritor concorrente não sai sem proteção.

Evidência: [Mobile BulkInsertHelper:41](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/vendor/OwnTV_Core/core/src/main/java/tv/own/owntv/core/database/BulkInsertHelper.kt:41) → [TV:41](C:/Users/renat/Downloads/OwnTV_Core/core/src/main/java/tv/own/owntv/core/database/BulkInsertHelper.kt:41).

### I2 — contrato completo e restaurável das definições — P1

**Diferença:** o Lote B resolveu transporte/SAF, mas os allowlists Mobile não incluem todas as opções já existentes de gravação/multiview. Só valores materializados são exportados. A validação recusa `sub_bg_opacity=-1`, apesar de esse valor ser o default gravável. O Core externo já alargou este domínio, mas diverge do ZIP.

**Solução:** inventário de preferências exportáveis, validação simétrica exportar/restaurar e cobertura das opções em falta. Definir explicitamente os defaults portáteis; conservar merge para backups antigos e opções exclusivas Mobile. Aceitar exatamente -1 e 0..100 para opacidade. Não exportar permissões SAF como se fossem transferíveis.

**Aceitação:** roundtrip de todas as opções graváveis, incluindo tempos de mudança, versões, barra/modos, áudio, gravação e opções por canal; default -1 preservado; -2/101 recusados; backup antigo e valores locais respeitados.

### I3 — classificar erros e remapear por identidade — P1, parte condicionada

**Diferença:** a TV separou falha de autenticação GCM de estrutura inválida/truncada. Persistem percursos Mobile em que IDs numéricos sem correspondência podem ser conservados num restauro seletivo. Um ID coincidente não prova que seja a mesma lista/perfil.

**Solução:** portar a classificação de erros após conferir BackupContainer; não converter toda exceção em palavra-passe errada. Separar cabeçalho/estrutura/JSON inválidos e truncagem detetável da falha de autenticação. Uma falha GCM pode significar senha incorreta ou conteúdo alterado: a mensagem não deve prometer distinguir as duas causas. Nos dados dependentes de perfil/fonte, usar apenas mapeamentos resolvidos. Não trocar todos os remaps de forma cega: chaves por URL legada/local precisam de tratamento próprio.

**Aceitação:** erros de estrutura/truncagem detetável/JSON conservam a sua classificação; autenticação falhada apresenta “palavra-passe incorreta ou ficheiro alterado”; duas instalações com IDs iguais/identidades distintas não cruzam opções; identidade igual com novo ID restaura; backups antigos/completos e cancelamento mantêm comportamento correto. O cenário integrado de colisão tem de ser reproduzido antes de mudar os helpers gerais.

Evidência: [Mobile SettingsRepository:2765](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/vendor/OwnTV_Core/core/src/main/java/tv/own/owntv/core/settings/SettingsRepository.kt:2765), [Mobile BackupManager:1681](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/vendor/OwnTV_Core/core/src/main/java/tv/own/owntv/core/backup/BackupManager.kt:1681), [implementação seletiva TV Core 67](C:/Users/renat/Downloads/OwnTV-main/docs/IMPLEMENTACAO_CORE_1.0.67_2026-10-12.md). Nos quatro ficheiros divergentes, validar o delta externo antes da portagem.

Referência da classificação de autenticação: [Mobile BackupContainer:211](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/vendor/OwnTV_Core/core/src/main/java/tv/own/owntv/core/backup/BackupContainer.kt:211).

### I4 — diagnóstico de catálogo sem segredos — P1

**Diferença:** Mobile HttpClient mascara algumas queries, mas regista mensagens/Throwable brutos e pode expor credenciais em paths, userinfo ou tokens arbitrários. O externo TV recente usa origem e campos controlados; ainda não coincide com o snapshot.

**Solução:** política única de diagnóstico seguro: código, tipo, duração e origem sem caminho/query quando não há forma segura de a mostrar. Não alterar URLs reais, User-Agent ou tratamento dos pedidos.

**Aceitação:** URLs sintéticas Xtream, userinfo, query e IOException aninhada não revelam segredos; código/status e duração continuam úteis.

Evidência: [Mobile HttpClient:45](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/vendor/OwnTV_Core/core/src/main/java/tv/own/owntv/core/network/HttpClient.kt:45) e [TV externo:137](C:/Users/renat/Downloads/OwnTV_Core/core/src/main/java/tv/own/owntv/core/network/HttpClient.kt:137).

### I5 — alterações sensíveis exigem autorização do perfil alvo — P1 quando há perfis protegidos

**Diferença:** TV ProfileManager usa autorização curta e de uso único, ligada ao alvo/PIN/ação, e atualização condicional. Mobile edita/apaga a entidade recebida; avatar pode regravar um snapshot antigo do perfil.

**Solução:** adaptar a autorização ao teclado e sheets Mobile; voltar a ler o alvo, validar no Core e atualizar campos pontuais. Preservar o formato PIN, a gestão Mobile e a proteção do último perfil.

**Aceitação:** PIN errado, PIN mudado entretanto, editar outro perfil protegido, avatar sem repor PIN antigo, cancelar e tentativa de apagar último perfil.

Evidência: [Mobile ProfileManager:54](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/vendor/OwnTV_Core/core/src/main/java/tv/own/owntv/core/profile/ProfileManager.kt:54), [TV autorização](C:/Users/renat/Downloads/OwnTV_Core/core/src/main/java/tv/own/owntv/core/profile/ProfileOperationAuthorization.kt), [Mobile ProfilesViewModel:61](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/app/src/main/java/tv/own/owntv/mobile/ui/profiles/ProfilesViewModel.kt:61).

## 5. Lote 2 — gravações que não declaram sucesso indevidamente

### R1 — RAW parcial e respostas HTTP — P1

**Diferença:** Mobile pode repetir o corpo de um arquivo RAW depois de já escrever bytes. A TV usa RecordingAttemptPolicy e recusa 206 sem Range validado.

**Solução:** uma interrupção depois de bytes termina como parcial; não anexar um corpo completo ao prefixo. Respeitar Retry-After de forma monotónica, cancelável e limitada à janela. Não inventar retoma por bytes sem contrato do servidor nem alterar HLS segmentado.

**Aceitação:** falha antes/depois do primeiro byte; RAW nunca duplica conteúdo; HLS retoma pelas sequências existentes; 206 inesperado recusado; cancelamento/limite temporal.

### R2 — finalização DASH/muxer e cópia SAF — P1

**Diferença:** Mobile DashRemux retorna sucesso antes de stop() do muxer, cuja exceção é ignorada. TV só conclui após copy, stop e validação.

**Solução:** portar conclusão validada, conservar fontes quando falha e verificar a cópia para SAF. Não mudar codecs ou ativar DASH por defeito.

**Aceitação:** falha de copy/stop/validação, falta de espaço e cancelamento não dão “concluído”; saída válida reproduz; fontes necessárias não são apagadas. MediaMuxer exige ensaio Android físico.

### R3 — mostrar indisponibilidade recuperável — P2

**Diferença:** TV tem verificação read-only de ficheiro/USB/permissão, atualizada apenas com a lista ativa, e verifica novamente ao abrir. Mobile tem validação de linha/perfil, mas não esta distinção de disponibilidade física.

**Solução:** estado “armazenamento indisponível”, “sem acesso” ou “ficheiro inexistente” sem apagar a gravação nem mudar destino. Verificar em IO e suspender fora do ecrã. Em ficheiros repartidos, validar segmentos ao abrir, sem varrer todas as partes periodicamente.

**Aceitação:** retirar/repor USB, revogar acesso, ficheiro parcial e gravação ativa; linha histórica preservada; nenhuma sondagem da lista em background.

Evidência: [Mobile RecordingEngine:308](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/vendor/OwnTV_Core/core/src/main/java/tv/own/owntv/core/recording/RecordingEngine.kt:308), [TV RecordingAttemptPolicy](C:/Users/renat/Downloads/OwnTV_Core/core/src/main/java/tv/own/owntv/core/recording/RecordingAttemptPolicy.kt), [Mobile DashRemux:69](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/vendor/OwnTV_Core/core/src/main/java/tv/own/owntv/core/recording/DashRemux.kt:69), [TV MediaAvailability](C:/Users/renat/Downloads/OwnTV_Core/core/src/main/java/tv/own/owntv/core/storage/MediaAvailability.kt), [Mobile RecordingsViewModel:154](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/app/src/main/java/tv/own/owntv/mobile/ui/screens/recordings/RecordingsViewModel.kt:154).

## 6. Lote 3 — Guia correto, responsivo e com menos trabalho invisível

### G1 — observar alterações reais, não só contagens — P1

**Diferença:** Mobile GuideViewModel observa contagens e aplica debounce().drop(1): pode absorver a primeira atualização estabilizada e não detetar edições com igual contagem. O Core privado **já fornece observeGuideChanges()**.

**Solução:** ligar a UI à API local existente; ativar trabalho exclusivo do Guia conforme subscrição/visibilidade, cancelar pedidos obsoletos e atualizar uma vez no regresso. Conservar cache concluído elegível por perfil/fonte/desvio. Não suspender sincronização geral, gravações ou reprodução.

**Aceitação:** emissões a 0/2/5/9 segundos, alteração de título/hora sem mudar contagem, fonte vazia→real, saída/retorno e mudança de perfil; sem reconstruções repetidas do Guia oculto.

### G2 — Loading/Data/Empty/Error e retry — P1

**Diferença:** null ainda em carregamento é apresentado como “sem guia”. Listas com Paging tratam itemCount zero como vazio sem distinguir falha.

**Solução:** estado explícito por linha e refresh/append; erro com Repetir. Conservar dados anteriores apenas quando pertencem ao contexto válido. Sem loops automáticos ou pedidos duplicados.

### G3 — intervalos EPG comuns ao desenho e ao toque — P1 funcional/P2 desempenho

**Diferença:** Mobile percorre todos os programas e o cursor temporal de um programa longo pode esconder um apontamento curto sobreposto. TV produz fragmentos sem sobreposição, mantendo a entidade e o início original.

**Solução:** adaptar o algoritmo puro, memorizar o resultado por dados/janela e partilhá-lo entre desenho e clique. Compor apenas a janela visível com margem controlada, preservando foco e semântica dos itens relevantes. Manter semântica TalkBack e alvos táteis; não copiar Canvas/D-pad integral.

### G4 — largura temporal e deslocamento coerentes — P2 condicionado

**Diferença:** um ScrollState Mobile é usado pelo cabeçalho e linhas com larguras diferentes. Há uma hipótese concreta de limites de scroll inconsistentes; não houve reprodução visual.

**Solução:** largura temporal total comum, incluindo linhas vazias/fim, com um dono claro do movimento horizontal ou sincronização controlada. Teste mínimo antes de refazer a grelha.

**Aceitação G2–G4:** programa longo→curto→continuação; clique abre o programa desenhado pelo início original; lacunas não inventam eventos; 23h acessível em linhas vazias/curtas; sem salto ao mover verticalmente; portrait/landscape/tablet/split-screen/fontes grandes.

### G5 — deduplicação conservadora e identidade do arquivo — P1/P2

**Diferença:** Mobile contém eliminação SQL por título/overlap que pode apagar programas de feeds diferentes; a TV conserva feeds e deduplica na leitura com fonte preferida, cobertura temporal forte e sinopse compatível.

**Solução:** remover a eliminação destrutiva apenas após testes e unificar a regra nos leitores do Guia, Live, pesquisa e agendamento. Não trocar só um helper e deixar leitores divergentes.

**Aceitação:** títulos iguais em programas distintos, pequenas sobreposições, programas curtos, sinopses diferentes e fonte preferida; arquivo e gravação usam a entidade original.

### G6 — XMLTV e cache com hash canónico — P2

**Diferença:** TV aceita timestamps com 12/14 dígitos, Z e offsets ±HHMM/±HH:MM. O spool Mobile reconstrói entidades sem calcular o hash canónico.

**Solução:** portar parser/testes e calcular hash no spool existente. Preservar os campos ricos, locks, fila, fingerprint e limite de 128 MiB da Mobile.

**Aceitação:** formatos válidos/legados, DST, UTC implícito e entradas inválidas; restore seguido de refresh idêntico não reescreve tudo por hashes vazios. Sem somar correção de request ao horário mostrado.

Evidência: [Mobile GuideViewModel:663](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/app/src/main/java/tv/own/owntv/mobile/ui/screens/guide/GuideViewModel.kt:663), [API local existente:55](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/vendor/OwnTV_Core/core/src/main/java/tv/own/owntv/core/repository/EpgRepository.kt:55), [Mobile GuideGrid:160](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/app/src/main/java/tv/own/owntv/mobile/ui/screens/guide/GuideGrid.kt:160), [TV GuideProgrammeCells](C:/Users/renat/Downloads/OwnTV-main/app/src/main/java/tv/own/owntv/features/epg/GuideProgrammeCells.kt), [Mobile EpgDao:62](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/vendor/OwnTV_Core/core/src/main/java/tv/own/owntv/core/database/dao/EpgDao.kt:62), [TV EpgDedupe:24](C:/Users/renat/Downloads/OwnTV_Core/core/src/main/java/tv/own/owntv/core/epg/EpgDedupe.kt:24), [Mobile XmltvTime:32](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/vendor/OwnTV_Core/core/src/main/java/tv/own/owntv/core/parser/XmltvTime.kt:32), [Mobile EpgProgrammeSpool:47](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/vendor/OwnTV_Core/core/src/main/java/tv/own/owntv/core/repository/EpgProgrammeSpool.kt:47).

## 7. Lote 4 — correções isoladas no player

| ID | Diferença real e adaptação | Aceitação |
|---|---|---|
| P1 | Surround deve reconfigurar apenas o motor proprietário. Mobile ainda pode recarregar mpv quando Exo está ativo; TV distingue exoActive. | Exo/mpv, pausa/reprodução; sem segundo motor ou pedido; volume continua 100%. |
| P2 | Reconstrução de superfície Exo deve ter job cancelável e limpar pending starts/subtitles/URL. Portar os pontos de invalidação, mantendo ownership do Lote A. | Duas reconstruções rápidas, canal novo durante espera e superfície antiga; não reaparece pedido anterior. |
| P3 | Retry de diferido deve conservar contexto: alternativas, formato declarado, posição, metadata e intenção de pausa. | Retry com URL renovada e pausa durante resolução; sem voltar ao direto/início. Benefício completo depende de D1 do lote 5. |
| P4 | mpv deve usar o formato declarado quando a URL é ambígua, não inferir apenas por extensão. | HLS .ts/sem extensão, HLS normal e TS real; HLS estrito continua respeitado. |
| P5 | Reter faixas por identidade após reconstrução, com aliases ISO e distinção de regiões/scripts. | Índices reordenados, faixa removida, idioma ambíguo, en/eng, pt-PT/pt-BR; sem escolha arbitrária. Não repor botões removidos. |
| P6 | Resumo HTTP por sessão: owner, fase, código, tipo e instante, antes da recuperação. É diagnóstico limitado, não uma cura de HLS. | Evento antigo não muda sessão nova; sem URLs/headers/tokens; sem novo polling contínuo. |

Evidência: [Mobile OwnTVPlayer:1163](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/vendor/OwnTV_Core/player-core/src/main/java/tv/own/owntv/player/OwnTVPlayer.kt:1163), [Mobile reconstrução Exo:2090](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/vendor/OwnTV_Core/player-core/src/main/java/tv/own/owntv/player/OwnTVPlayer.kt:2090), [TV ArchiveRetryContext](C:/Users/renat/Downloads/OwnTV_Core/player-core/src/main/java/tv/own/owntv/player/ArchiveRetryContext.kt), [TV MpvStreamFormat](C:/Users/renat/Downloads/OwnTV_Core/player-core/src/main/java/tv/own/owntv/player/MpvStreamFormat.kt), [TV AudioTrackRetention](C:/Users/renat/Downloads/OwnTV_Core/player-core/src/main/java/tv/own/owntv/player/AudioTrackRetention.kt), [TV PlaybackSessionDiagnostics](C:/Users/renat/Downloads/OwnTV_Core/player-core/src/main/java/tv/own/owntv/player/PlaybackSessionDiagnostics.kt).

Não portar OwnTVPlayer/LiveTuneController inteiros. As revisões Mobile de item/comando/áudio, PlaybackEventOwner, Cast, poupança de dados e comandos de media são invariantes de regressão.

## 8. Lote 5 — diferidos e resolução de ligações

| ID | Adaptação proposta | Dependência e aceitação |
|---|---|---|
| D1 | Resolver diferidos para contexto tipado: fonte, relógio, alternativas e tipo de manifesto; propagar cancelamento. Mobile ainda inicia por URL simples. | Completa P3. Programa antigo conserva seleção/posição e correção temporal única; renovar Stalker não absorve cancelamento; não somar outra hora ao request. |
| D2 | Catchup M3U por capacidade real: none/off/false não ativam; template útil pode ativar sem tag redundante; resolver path/query relativos e preservar tokens. | Sync e leitores usam a mesma regra. URLs absolutas/relativas, append/query, subdiretório e retenção. Não promete dias que o fornecedor não oferece. |
| D3 | Referer da lista chega também ao Live Core; headers do canal prevalecem; Stalker/local não recebem identidade genérica. | Cast/diferido Mobile já têm helper adequado. Testar playlist/segmento/chave e precedência, sem User-Agent global. |
| D4 | Falha Stalker nula/expirada/timeout deve ser explícita e pertencer ao pedido atual. Adaptar tanto a resolução inicial Mobile como o Core. | Portal nulo não inicia decoder nem troca motores; mudar canal/perfil cancela; não publicar falha antiga. |
| D5 | Timeshift local existente distingue preparação 503 com marcador privado e fim 410, com repetição limitada só no loopback de sessão. | Sublote condicionado ao modo existente; incluir init.mp4/fMP4 e HEAD. Não ativar gravação em loop por defeito, nem tratar 503 remoto como exceção local. |

Evidência: [Mobile LiveEpgReader:501](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/vendor/OwnTV_Core/core/src/main/java/tv/own/owntv/core/live/LiveEpgReader.kt:501), [TV ResolvedArchiveSource](C:/Users/renat/Downloads/OwnTV_Core/core/src/main/java/tv/own/owntv/core/epg/ResolvedArchiveSource.kt), [TV CatchupUrl:175](C:/Users/renat/Downloads/OwnTV_Core/core/src/main/java/tv/own/owntv/core/epg/CatchupUrl.kt:175), [Mobile LiveTuneController:777](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/vendor/OwnTV_Core/player-core/src/main/java/tv/own/owntv/player/LiveTuneController.kt:777), [TV LocalPlaylistReadiness](C:/Users/renat/Downloads/OwnTV_Core/player-core/src/main/java/tv/own/owntv/player/LocalPlaylistReadiness.kt).

A preferência HLS do direto não deve ser usada para inventar a capacidade de arquivo. Formatos alternativos de arquivo só entram com contexto explícito e políticas já escolhidas; nenhuma passagem automática indiscriminada a TS.

## 9. Lote 6 — usabilidade, configurações e instalação

Executar os sublotes separadamente, evitando uma entrega grande sem validação intermédia.

### U1 — versões do canal com Guardar/Cancelar e erro recuperável

A Mobile grava cada deslocação imediatamente. Adaptar o rascunho da TV à bottom sheet: reordenação/restauro provisórios, Guardar/Cancelar, estados de carregamento/erro e retry. Guardar valida perfil/fonte/canal dentro da operação; erro não apaga o rascunho. Adicionar/desassociar podem continuar imediatos se o efeito for explicado. Canais ocultos continuam fora da prioridade.

Aceitação: cancelar/reabrir conserva ordem anterior; guardar persiste; erro mantém rascunho; troca de perfil não grava no alvo errado; toque/reordenar e TalkBack. Arrastar não é requisito novo desta portagem.

### U2 — escrita atómica e valores efetivos

A UI Mobile já tem herança e rascunho por canal. Portar apenas garantias em falta: leitura válida antes de editar, snapshot imutável no Guardar, preservação do legado nas opções não alteradas e validação do perfil esperado dentro da transação.

Se a seleção em lote usa pesquisa truncada, adaptar a projeção completa e leve da TV: o limite visual não pode limitar “selecionar todos”. Confirmar essa necessidade na rota Mobile antes de adicionar nova API.

### U3 — pesquisa de definições sem acentos

Portar normalização NFD/Locale.ROOT e cache dos textos, preservando ações e valores atuais. “definicoes”, “reproducao” e “atualizacoes” encontram os rótulos traduzidos. Não importar o catálogo TV de opções.

### U4 — regresso ao canal atual por ID

Mobile já guarda/destaca o canal, mas pode pedir foco só numa linha composta. Adaptar âncora/Paging/bring-into-view ao toque, sem lógica D-pad nem autoplay. Testar canal fora da viewport/primeira página, fullscreen zapping, grupos, pesquisa e scroll manual. É uma lacuna estática a reproduzir antes de mudar o fluxo.

### U5 — importação Xtream/M3U mais tolerante

TV limpa espaços e &amp;, reconhece get.php e só converte nova origem após resposta Xtream válida, com prazo/corpo limitados. Adaptar ao setup Mobile, preservando credenciais codificadas, subdiretório, Referer, UA e EPG. Cancelamento não pode iniciar fallback. Listas existentes não são convertidas silenciosamente.

Portar também tolerância Xtream por campo quando o delta e os testes confirmarem a necessidade; flags falsas não viram extensões “.false”. Sem ativar filmes/séries ou mudar a preferência HLS.

### U6 — atualizador e CI conscientes da arquitetura

Mobile já procura atualizações, mas escolhe o primeiro asset não-x86 elegível. TV escolhe ABI explicitamente e verifica bibliotecas nativas do APK. Portar o contrato mantendo applicationId, feeds e assinatura Mobile; falhar com mensagem útil se não há APK compatível.

A CI Mobile ainda tem comentários antigos de “sem atualizador” e usa seleção do primeiro APK. Contudo a Mobile atual não usa os mesmos splits da TV: **não copiar o nome universal fixo TV**. Selecionar saída pelos metadados reais do build Mobile e validar conteúdo/ABI.

Adicionar à CI verificações explícitas dos testes Core privado/player e da integração personalizada; um pin upstream isolado não demonstra que os inputs privados foram validados. Não publicar nem alterar assinatura neste lote sem pedido próprio.

Evidência: [Mobile ChannelVersionsSheet:34](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/app/src/main/java/tv/own/owntv/mobile/ui/screens/live/ChannelVersionsSheet.kt:34), [TV ChannelVersionsDialog:43](C:/Users/renat/Downloads/OwnTV-main/app/src/main/java/tv/own/owntv/features/live/ChannelVersionsDialog.kt:43), [Mobile SettingsChannelPlaybackPage:94](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/app/src/main/java/tv/own/owntv/mobile/ui/screens/settings/SettingsChannelPlaybackPage.kt:94), [TV SettingsSearchText](C:/Users/renat/Downloads/OwnTV-main/app/src/main/java/tv/own/owntv/features/shell/components/SettingsSearchText.kt), [Mobile LiveScreen:126](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/app/src/main/java/tv/own/owntv/mobile/ui/screens/live/LiveScreen.kt:126), [TV XtreamM3uLink](C:/Users/renat/Downloads/OwnTV_Core/core/src/main/java/tv/own/owntv/core/parser/XtreamM3uLink.kt), [Mobile UpdateApkSelection](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/vendor/OwnTV_Core/core/src/main/java/tv/own/owntv/core/update/UpdateApkSelection.kt), [TV UpdateApkSelection](C:/Users/renat/Downloads/OwnTV_Core/core/src/main/java/tv/own/owntv/core/update/UpdateApkSelection.kt), [CI Mobile](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/.github/workflows/android.yml).

## 10. Lote 7 — consumo CPU/RAM, diagnóstico e limpeza

### M1 — baseline real antes/depois

Adaptar a ideia TV de medição por percurso, usando seletores estáveis e fixtures Mobile. A Mobile já gera Baseline Profile, mas isso não mede por si só fluidez nem comprova que o percurso passou no ecrã certo.

Percursos separados: arranque; scroll de lista; Guia quente; período EPG por carregar; versões; abrir/fechar player; background/retorno. Sem credenciais em fixtures e sem abrir streams de fornecedor para medir só a UI. Medir release no mesmo telefone/tablet e condições; repetir pelo menos cinco iterações.

Recolher frame time/jank, CPU, alocações/GC e leituras de base de dados. Separar duração de IO de tempo CPU; frames lentos da UI de frames perdidos do vídeo. Diagnóstico opcional, limitado e sem escrita/polling quando desligado. Não prometer “2x–4x” sem medição.

### M2 — aplicar só otimizações com evidência

Prioridades prováveis: suspender trabalho de Guia oculto, usar invalidadores reais, partilhar modelo temporal e compor apenas células visíveis. Não aumentar pools de threads, fazer preload indiscriminado ou transferir decoders para outra thread por intuição. Medir antes de alterar reservas/quota sob mutex ou ingestão EPG.

### M3 — limpeza sem destruir funcionalidades preservadas

Inventário de referências Kotlin/recursos/DI/Gradle/R8 e chamadas externas antes de retirar código. Portar da TV a metodologia, não a lista de ficheiros mortos: navigation/Room/KSP e serviços usados diferem entre projetos.

Candidatos concretos: comentários CI “sem atualizador”, comentários antigos de PiP no Manifest e descrições/rotas sem consumidores comprovados. Corrigir comentários não exige apagar campos compatíveis de backup ou dados ocultos.

Não copiar automaticamente a política Coil TV: a Mobile não tem o interceptor que impunha cache longa às respostas falhadas. Só adaptar política adicional se testes do Coil efetivamente usado demonstrarem necessidade.

Evidência: [TV UiWorkWindow](C:/Users/renat/Downloads/OwnTV-main/app/src/main/java/tv/own/owntv/diagnostics/UiWorkWindow.kt), [preparação de medição TV](C:/Users/renat/Downloads/OwnTV-main/docs/FASE_E1_E2_MEDICAO_E_VALIDACAO_TV_2026-10-14.md), [BaselineProfile Mobile](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/baselineprofile/src/main/java/tv/own/owntv/mobile/baselineprofile/BaselineProfileGenerator.kt).

## 11. O que não copiar e o que fica condicionado

**Rejeitado neste plano:**

- Substituição integral do Core privado, OwnTVPlayer, EpgRepository ou base de dados.
- Interface TV com foco/D-pad, percentagens fixas de painéis, gestos de pressão de vários segundos obrigatórios e autoarranque por Acessibilidade.
- Reintrodução de PiP, meteorologia, OpenSubtitles UI, favoritos/histórico/filmes/séries ou botões de áudio removidos.
- Ganho/volume interno diferente de 100%.
- Mais buffer, software decode, H.264 especial, User-Agent ou atrasos obrigatórios globais.
- Alterar o recheck de handover Mobile de 200 para 300 ms apenas por ser o valor TV. Ambos já esperam eventos e confirmação local; isso não prova fecho da sessão no servidor.
- Multiview/reservas/preview TV completos sem necessidade Mobile e medição de memória/bateria.
- Partilhar agora a mesma pasta Core entre TV e Mobile. Uma futura extração de helpers comuns exige contratos e CI separados; não usar o override global para unir projetos.

**Condicionado a reprodução/testes:**

- Watchdogs após pausa/retoma: o achado TV também existe na Mobile, mas não há uma solução pronta consolidada a copiar. Testar READY→pausa→READY sem novo callback e BUFFERING→pausa→BUFFERING; só depois corrigir o rearmar, sem criar outro watchdog.
- LL-HLS e crescimento residual da cache mpv: precisam de fixtures e evidência; não justificam mudar todos os timeouts.
- Restrições de visibilidade após associações EPG: testar a política final Mobile; não copiar uma rota TV que o próprio relatório identifica como falha.
- JNI, novas flags TS e nova libmpv: nenhuma evidência desta auditoria exige atualização nativa.
- Timeshift local: corrigir estado do modo existente; qualquer expansão/padrão ativo exige orçamento de disco e bateria.
- Remap seletivo de backup e âncora de regresso à lista: testes integrados/visuais antes de generalizar os helpers.

## 12. Matriz de validação e conclusão de cada lote

| Eixo | Ensaios mínimos |
|---|---|
| Build | Compilar Standard Release, testes Mobile/Core privado/player e lint vital; i18n sem supressões novas. Testar ABI suportadas quando muda instalação/nativo. |
| Reprodução | Exo e mpv conforme escolha; HLS estrito; A→B→C→A; erro real antes do primeiro frame; pausa durante buffering; superfície antiga; Cast e poupança de dados. |
| Mobile lifecycle | Home/regresso, comandos da notificação durante ausência, resize/split-screen; recriação real do processo para operações SAF. |
| Guia/diferidos | Hoje/ontem/vários dias dentro da retenção; overlap; fonte/offset; clique e timestamp original; loading/error/retry; trabalho oculto. |
| Gravações/armazenamento | RAW parcial, HLS segmentado, DASH/muxer, interno/SAF/USB, falta de espaço/permissão, cancelamento e ficheiros repartidos. |
| Configuração/backup | Guardar/cancelar/erro, mudança de perfil durante escrita, roundtrip novo e antigo, IDs coincidentes e valores Mobile-only. |
| Interface | Telefone portrait/landscape, tablet, janela reduzida, teclado nativo, fontes grandes e TalkBack; sem exigir D-pad. |
| Consumo | Antes/depois no mesmo hardware, diagnóstico desligado na comparação principal, sem alegar aceleração por testes JVM. |

Cada lote termina com: alterações limitadas, testes relevantes, resultado documentado, limitações físicas claras e lista de regressões por validar. Se exigir schema novo, justificar e testar migração antes de incluir; este plano não aprova copiar migrações da TV em bloco.

Os 1.651 testes descritos no Lote B são a validação anterior registada, **não foram repetidos nesta análise**. Neste trabalho houve inspeção estática, comparação de ficheiros/hash e consulta de documentação oficial; nenhum teste Android, benchmark, ligação ao fornecedor ou medição de CPU/RAM foi executado. Não se garante, a partir deste relatório, ausência de bloqueios do fornecedor ou cortes de áudio em hardware específico.

## 13. Documentação oficial usada para rever as decisões

- [Layouts por janela disponível](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes): adaptar à largura/altura atuais, incluindo janela reduzida; a Mobile já tem base adaptativa.
- [Corrotinas e lifecycle](https://developer.android.com/topic/libraries/architecture/coroutines): o trabalho exclusivo de um ecrã deve acompanhar o seu ciclo de vida, sem confundir isso com serviços funcionais em background.
- [Eventos do Media3](https://developer.android.com/media/media3/exoplayer/listening-to-player-events): BUFFERING, playWhenReady e isPlaying têm significados diferentes; preservar intenção e ownership.
- [Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview): medir percursos reais para avaliar desempenho, separadamente da geração de perfil.

## Próximo passo recomendado

Recomendação original de 15/10: começar pelo Lote 1, com subentregas I1 → I2/I3 → I4 → I5, preservando os lotes A/B Mobile. Após a implementação dos lotes 1/2 em 16/10, o próximo é o **Lote 3 — Guia e desempenho**. Preparar medição antes de otimizar a grelha. Lotes 3–7 não foram implementados.

