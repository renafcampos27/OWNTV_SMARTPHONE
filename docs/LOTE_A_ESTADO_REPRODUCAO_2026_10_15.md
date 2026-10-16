# Lote A — estado e propriedade de reprodução

Data: 15/10/2026. Estado: implementado na Mobile e no Core privado; compilação, testes e lint concluídos. Sem APK e sem testes físicos.

Origem: lote A da AUDITORIA_MOBILE_COUNCIL_2026_10_12.md. Lotes B/C/D continuam separados; não foram implementados neste pedido.

## A1 — pausa e retoma da Activity

- onStop usa a intenção playbackRequested, incluindo BUFFERING, em vez de depender de isPlaying.
- A pausa é explícita. O regresso só retoma a pausa pertencente ao mesmo motor, item e revisão de comando, sem erro terminal. Os tickets são consumidos uma vez.
- Uma ação posterior de Play/Pause da notificação invalida a retoma anterior. Não se usa toggle para decidir a retoma de lifecycle.
- A ocultação automática de vídeo tem propriedade separada: a revisão do modo áudio distingue uma escolha explícita do utilizador de uma reconexão ou mudança de posição.
- Uma recuperação do mesmo motor em segundo plano não perde a possibilidade de devolver a imagem. Devolver imagem não dá Play nem desfaz uma pausa manual. Uma escolha explícita posterior de apenas áudio permanece respeitada.

Ficheiros: MainActivity.kt; playback/BackgroundPlaybackHold.kt; propriedades de identidade e revisão em PlaybackEngine.kt, OwnTVPlayer.kt e LivePreviewEngine.kt.

## A2 — continuação Exo → mpv

- A passagem passa a ter um job cancelável e uma intenção pendente identificada.
- Depois da espera, valida identidade da continuação, geração, revisão do comando, intenção de reprodução, URL e motor ativo.
- Nova carga, stop e libertação invalidam a continuação; A → B → A já não depende apenas da comparação de URL.
- Pausa durante a espera cancela o job. Um novo Play agenda uma continuação atual, em vez de deixar o leitor sem forma de retomar.
- Um seek durante a espera atualiza a posição pendente.
- A troca manual Exo → mpv utiliza a mesma proteção; não mantém uma segunda rotina com guarda apenas por URL.
- Mantida a espera existente de 600 ms apenas nesta passagem entre motores. Não foi acrescentado atraso geral ao zapping.

Âmbito: diferidos/legendas e troca manual de motor de reprodução suportada neste caminho. Não demonstra a resolução de todos os bloqueios do zapping em direto nem de recusas do fornecedor.

## A3 — eventos Exo de diferidos

- Cada início recebe um ticket novo, anexado ao MediaItem.
- Callbacks passam por AnalyticsListener e validam o ticket na timeline do evento: reprodução, buffering, fim, dimensão, primeiro frame, cues, tracks e erro.
- Eventos de decoder/formato e AudioWatchdog usam a mesma validação.
- Stop invalida o ticket antes da libertação e remove os listeners.
- Repreparar legendas também renova a identidade; trabalho assíncrono de ajuste de legendas é cancelado/validado contra a reprodução original.
- No OwnTVPlayer, ações lançadas por erro/fim Exo validam geração, revisão e motor antes de atuar.

A existência de eventos tardios no dispositivo não tinha sido demonstrada pela auditoria. Esta implementação acrescenta a proteção e testes da política de aceitação; não equivale a reproduzir uma corrida real do Media3.

## Validação

Comando de validação: compileStandardReleaseKotlin, testStandardDebugUnitTest, Core testDebugUnitTest, player testDebugUnitTest e lintVitalStandardRelease, sem paralelismo entre builds.

| Verificação | Resultado |
|---|---:|
| Mobile | 78 testes, zero falhas/erros/ignorados |
| Core | 1.114 testes, zero falhas/erros/ignorados; código do módulo core não alterado e tarefa reutilizada |
| Player | 448 testes, zero falhas/erros/ignorados |
| Total | 1.640 |
| Compilação Standard Release Kotlin | Passou |
| Lint vital Standard Release | Passou |
| Inventário de literais | Passou |
| git diff --check | Passou |

Novos testes: sete de BackgroundPlaybackHold e quatro de PlaybackEventOwner/PlaybackRequestStamp. Cobrem intenção durante buffering, notificação, pausa repetida, motor/item/erro, consumo único, reposição de imagem, escolha manual de áudio e rejeição de tickets/eventos anteriores. São testes unitários de política, não testes instrumentados da Activity, decoder ou rede.

## Aceitação física pendente

1. Segundo plano desligado: Home durante BUFFERING; não começar som posteriormente. Regressar e confirmar retoma do pedido válido.
2. Play/Pause na notificação antes do regresso; o regresso não deve inverter a escolha.
3. Segundo plano ligado: ocultar imagem automaticamente, provocar reconexão, regressar; devolver imagem mantendo uma pausa manual.
4. Diferido: iniciar passagem Exo → mpv e escolher B/A antes de terminar; não reaparecer a posição/continuação antiga. Pausar, deslocar posição e voltar a dar Play.
5. Confirmar erros reais antes do primeiro frame, tracks, áudio AAC/MP2 e legendas; nenhum erro atual pode ficar escondido pela filtragem.
6. Regressão de direto: A/B/C/A, sair para lista e reabrir, mantendo HLS e escolha de motor.

Não foram medidos CPU, RAM, latência ou som num smartphone/box. Mantidos volume interno de 100%, buffers, latência, HLS estrito e exceções por canal. Sem alteração da base de dados, backups, assinatura ou versão da libmpv. OwnTV-main e Core externo não foram editados.

Cópia anterior dos três ficheiros principais: C:/Users/renat/AppData/Local/Temp/owntv-before-loteA-20261015.
