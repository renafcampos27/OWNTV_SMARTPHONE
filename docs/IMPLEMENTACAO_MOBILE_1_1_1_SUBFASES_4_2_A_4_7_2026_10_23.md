# OwnTV Mobile — subfases 4.2 a 4.7

Data: 23/10/2026. Estado: implementação e validação automática concluídas; aceitação física pendente.

Âmbito exclusivo: `C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main`, incluindo o Core privado `vendor/OwnTV_Core`. A autorização atual abrange todas as subfases restantes. Não foi gerado APK.

## Resultado e novas configurações

| Subfase | Implementação | Configuração/valor inicial |
|---|---|---|
| 4.2 — saída de áudio | Exo identifica a saída do AudioTrack em reprodução e acompanha mudanças de rota. Sem sondagem periódica. Callback desligado no flush/reset/release; origem antiga não publica a rota de um novo AudioTrack. | Reprodutor de vídeo → Som → Saída de áudio. Distingue confirmação, estimativa e saída desconhecida. |
| 4.3 — sincronismo | Compensação independente para altifalante, auscultadores com fio, Bluetooth, HDMI, ARC/eARC, USB e outra saída. Soma à base global, por canal ou guardada; não regrava essa base. | Som → Calibrar sincronismo por saída. Todas as compensações começam em 0 ms; intervalo ±5000 ms, passo 25 ms. |
| 4.4 — FPS/AFR | FPS por timestamps de apresentação, janela de 600 intervalos e pelo menos 4 s de cadência estável. Distingue taxas fracionárias e inteiras. Amostragem de frames só com AFR ou estatísticas ativos. | Mantém a preferência AFR existente. Não é ligado universalmente. |
| 4.5 — SDR | Preferência global e exceção por canal. Em Exo/HLS com SDR e HDR explicitamente identificados, conserva variantes SDR. Não altera outros canais por aprendizagem automática. | Reprodutor de vídeo → Imagem → Preferir variantes SDR. Desligado por defeito. Exceção na configuração de cada canal: herdar, ativar ou desativar. |
| 4.6 — perdas de vídeo | Janela móvel, proporção `perdidos / (apresentados + perdidos)`, contadores do decoder e reset de janela por decoder/pausa/buffering. Um aviso detalhado por sintonia e memória limitada. | Informações da reprodução → Perdas de vídeo nesta janela; registo detalhado quando ativo. Sem aviso de erro por cima de um canal saudável. |
| 4.7 — recuperação | Uma reconstrução do mesmo leitor por sintonia/item perante degradação grave persistente. Reutiliza retenção de faixas/posição e a libertação/cancelamento existentes. Não muda motor ou formato. | Reprodutor de vídeo → Diagnóstico → Recuperar degradação grave de vídeo. Desligado por defeito. |

As novas opções também entram na pesquisa das Definições. O volume interno mantém-se em 100%; estas compensações alteram sincronismo, não ganho.

## 4.2 e 4.3 — limites e backup

O AudioTrack fornece a rota efetivamente usada por Exo enquanto está a reproduzir. Dispositivo Bluetooth ligado não é prova de que o áudio passa por ele. A consulta pública do Android 13+ fornece uma rota antecipada para os atributos de média: é apresentada como estimativa, nunca como confirmação. Sem evidência disponível, a saída é «Desconhecida» e aplica 0 ms. Para mpv/Android anterior a 13 pode não existir uma classificação disponível; não se inventa a saída a partir de todos os dispositivos conectados.

O observador de processo usa callbacks de dispositivos/MediaRouter e retoma de Activity. Cada sink Exo gere os seus callbacks AudioRouting, libertando referências no encerramento. Usa-se a ponte pública AudioTrackProvider ainda suportada no Media3 1.11.1 para obter o AudioTrack; a API AudioOutput mais recente não expõe esse objeto. A criação delega no fornecedor original, preservando atributos, configuração, buffer e sessão. Não há reflexão.

O painel Som apresenta a conta da configuração global. As informações de reprodução apresentam também a base efetiva do canal/item, compensação da saída e total. Valor positivo atrasa o som; negativo adianta-o. Total limitado a ±5000 ms, sem perder os ajustes separados. Alterar sincronismo durante reprodução pode provocar uma correção momentânea da imagem; deixar 0 ms se não existe desfasamento.

O backup completo inclui `audio_delay_by_output`, `prefer_sdr`, `video_degradation_recovery` e o SDR das opções por canal. A calibração tem seleção independente no restauro: marcada para o mesmo aparelho, desmarcada para outro/identidade desconhecida. Mesmo escolhendo importar as restantes definições de hardware, a seleção própria da calibração prevalece. Ficheiros antigos preservam campos que não conheciam. A calibração guarda apenas classes de saída e milissegundos; não acrescenta MAC, nome de auscultadores ou identificadores de hardware.

## 4.4 — precisão com custo limitado

PTS usa memória fixa; intervalos duplicados não criam FPS novos e uma descontinuidade descarta a janela. Não se ordenam amostras nem se atualiza a interface a cada frame. Live consulta a janela no máximo cinco vezes por medição; o outro Exo reutiliza o tick existente. Callbacks de frames conservam a identidade da origem para não misturar sintonias. Sem AFR/estatísticas, não há recolha PTS.

A taxa medida pode refinar 60 para 59,94 ou 24 para 23,976. Uma diferença grande conserva a declaração válida: perder frames alternados não deve transformar um canal de 60 em 30 FPS no AFR. Quando falta declaração, é usada a medição estável. A estimativa mpv continua dependente dos dados nativos; o novo ajuste rejeita arredondamentos largos e exige dois resultados coerentes. Isto não prova que uma declaração do fornecedor ou estimativa nativa esteja certa.

O Android continua a decidir se aceita o pedido de frequência. Não se alterou a política de mudança sem interrupção dos smartphones.

## 4.5 — filtragem HLS sem novos pedidos

A preferência é capturada na criação da origem. O parser original lê a resposta normalmente; uma observação limitada a 512 KiB acompanha apenas o manifesto inicial. Não existe segundo pedido, segundo parse integral, transformação de segmentos ou captura nas atualizações periódicas das playlists de segmentos.

Só filtra se identifica simultaneamente `VIDEO-RANGE=SDR` e `PQ`/`HLG`, e encontra variantes correspondentes. HDR-only, ausência de identificação suficiente, excesso do limite ou correspondência vazia mantêm o manifesto original. São preservados grupos de áudio, legendas, chaves/DRM, variáveis e content steering do objeto interpretado; o cliente HTTP, headers e identidade da origem não são substituídos.

Não é conversão HDR→SDR. mpv não recebe este filtro. No Exo de arquivos/gravações, itens com legendas externas mantêm a fábrica original que junta essas legendas; não se perde funcionalidade para aplicar SDR. `hdr_enabled=false` antigo não é migrado para `prefer_sdr=true`.

## 4.6 e 4.7 — critérios e contenção

Diagnóstico: janela móvel de cerca de 30 s, até 64 pontos e sem timer adicional. Usa frames efetivamente contados como apresentados/perdidos pelo decoder, não callbacks de submissão. Uma janela com pelo menos 150 perdas e mais de 20% produz um aviso detalhado único por sintonia. Buffering, pausa, falta de superfície, velocidade diferente de 1×, rollback dos contadores e intervalos de observação excessivos interrompem a janela; novo decoder limpa amostras anteriores.

Recuperação opcional: mais de 100 perdas e mais perdas que apresentações durante cerca de 10 s, condição mantida por pelo menos mais 2 s, reprodução ativa, superfície válida e pelo menos 2 s de buffer. Uma tentativa por sintonia/item, mesmo após reconstrução do decoder ou reconexão. Não reinicia o orçamento em cada poll. Arquivos sem retoma segura não são reconstruídos automaticamente. A regra usa o mesmo motor também em Automático, evitando introduzir outro percurso de fallback sem ensaio físico.

Estes limiares são uma heurística conservadora, não uma norma Android nem prova de avaria de rede/codec. A opção fica desligada até teste comparável. Não corrige fotogramas em falta na origem, limitações físicas de descodificação ou sessões recusadas pelo fornecedor. Os restantes mecanismos de recuperação existentes mantêm-se.

## Validação automática

Comando, sem montagem de APK:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:compileStandardReleaseKotlin :app:testStandardDebugUnitTest :OwnTV_Core:core:testDebugUnitTest :OwnTV_Core:player-core:testDebugUnitTest :app:lintVitalStandardRelease :OwnTV_Core:core:compileDebugAndroidTestKotlin --no-parallel --console=plain
```

- **BUILD SUCCESSFUL — 51 s.** Compilação Release da app e Debug/Release das dependências.
- **1843 testes JVM aprovados:** 119 Mobile + 1198 Core + 526 Player; zero falhas, erros ou testes ignorados. São 23 testes novos relativamente à subfase 4.1.
- Novos testes: offsets/classes/clamping, SDR por canal e remapeamento de backup, taxas fracionárias/PTS/descontinuidades, proteção contra metade do FPS, manifestos mistos/HDR-only/sem identificação, captura limitada e bytes preservados, percentagens/janelas/reset/aviso único e orçamento/guardas da recuperação.
- Teste Android de contrato DataStore/backup ampliado e compilado, **não executado**: seleção da calibração, flags globais, importação antiga e preservação de definições do aparelho.
- `lintVitalStandardRelease`: **No issues found**. Pesquisa das Definições corrigida após o teste de cobertura detetar os novos campos em falta.
- Inventário i18n aprovado contra o snapshot: Core 310 baseline + 5731 entradas técnicas; Mobile 0 + 585. Sem aumento da dívida.
- `git diff --check` aprovado. Avisos de conversão futura LF/CRLF já presentes no checkout não são erros deste lote.

Log final: `%TEMP%/owntv-mobile-111-phase4-rest-verified-20261023.log`. Evidência: [MOBILE_111_FASE4_RESTANTE_20261023.json](validacao/MOBILE_111_FASE4_RESTANTE_20261023.json).

## Preservação e rollback

Snapshot SHA256 verificado: `%TEMP%/owntv-mobile-before-phase4-rest-111-20261023`, **1468 entradas** preexistentes. O ficheiro `manifest.json` identifica as cópias originais e `protected-verification.json` confirma **67 entradas protegidas** inalteradas, incluindo base de dados, LiveTuneController, PlaybackSession, SourceDrain, PlaybackHttpCalls, superfícies, buffer e fontes Java FFmpeg. Não repor o checkout inteiro: contém trabalho anterior não commitado; usar o inventário para um rollback seletivo se for necessário.

Mantidos: schema 52, Media3 1.11.1, libmpv 2026.10.4, HLS estrito/exceções autorizadas, preferência de motor, políticas de buffer/latência/retoma e ganho interno. Não se alterou o projeto TV nem o Core externo. Sem assinatura, instalação, commit ou publicação.

## Aceitação física pendente

Nenhum dispositivo ADB ligado. Não existe medição física de CPU, ganho de fluidez, codecs ou rota/AFR.

1. Com todas as opções novas nos valores iniciais, testar A→B→C→A, pausa, lista/fullscreen, rotação e Home/retoma. Confirmar som e volume pelo sistema.
2. Em Exo, trocar altifalante/auscultadores/Bluetooth/USB/HDMI durante reprodução. Confirmar saída efetiva, ausência de callbacks antigos e compensações independentes. Em mpv/Android antigo, confirmar apresentação honesta de saída desconhecida quando não há evidência.
3. Testar global + canal + ajuste guardado e calibração de saída, incluindo limites positivos/negativos. Exportar/restaurar num ambiente controlado, com a calibração selecionada e desmarcada; testar transferência para outro aparelho.
4. Com AFR ligado, testar 23,976/24/25/29,97/50/59,94/60 e voltar a desligar. Confirmar taxas no diagnóstico e frequência efetiva aceite pelo ecrã.
5. Ativar SDR apenas num canal de ensaio com manifesto misto. Testar HDR-only, áudio/legendas, DRM e retorno a um canal sem exceção; não deve existir contaminação.
6. Observar perdas com recuperação desligada. Só depois testar a opção ligada numa origem com degradação grave reproduzível: uma única reconstrução; sem nova tentativa em ciclo, perda de motor/formato ou atuação em pausa/buffering/arquivo sem retoma.

## Referências primárias consultadas

- [Android AudioRouting](https://developer.android.com/reference/android/media/AudioRouting): rota do AudioTrack e callbacks.
- [Android AudioManager](https://developer.android.com/reference/android/media/AudioManager#getAudioDevicesForAttributes(android.media.AudioAttributes)): rota antecipada para atributos de áudio, API 33+.
- [Android Frame rate](https://developer.android.com/media/optimize/performance/frame-rate): precisão fracionária e decisão final da plataforma.
- Fontes locais do Media3 1.11.1: DefaultAudioSink, DefaultAudioTrackProvider, DefaultRenderersFactory, HlsPlaylistParser, HlsMultivariantPlaylist e DefaultMediaSourceFactory.
- [OwnTV Core 1.0.69](https://github.com/ahXN00/OwnTV_Core/tree/core-1.0.69): conceitos adaptados de AudioOutputDelay, PtsFrameRate, SdrVariants, FrameDropWatch, SmoothnessWatch e SdrHlsRouting; não substituição integral.
