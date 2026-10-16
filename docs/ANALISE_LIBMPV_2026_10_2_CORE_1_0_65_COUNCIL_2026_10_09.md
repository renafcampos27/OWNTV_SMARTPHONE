# OwnTV Mobile — libmpv 2026.10.2 e Core 1.0.65

Data: 09/10/2026. Estado deste documento: análise anterior à implementação. A implementação autorizada posteriormente está documentada em [relatório de integração](IMPLEMENTACAO_LIBMPV_2026_10_2_CORE_1_0_65_2026_10_10.md). Não foi gerado APK.

## Base e método

Comparação das tags públicas com o código atual de `vendor/OwnTV_Core`, incluindo adaptações Mobile. Três especialistas: biblioteca nativa; reprodução; dados/gravações/restauro. Council por revisão cruzada, com contestação de redundâncias e riscos.

- Mobile inclui explicitamente o Core privado; não usa o Core externo da TV.
- Dependência atual: libmpv 2026.10.1, catálogo do Core privado.
- Base de dados local: 51; upstream 1.0.65: 47.
- Backup local: 25; upstream: 24.
- Estes números são versões de formatos, não uma comparação de qualidade ou de quantidade de funcionalidades.
- Não foram executados builds, testes Android ou medições de dispositivos nesta análise.

## libmpv 2026.10.2

Mesmo mpv fixado em 3186d369f9f090cd1363be0ac46a037824b702c6 e FFmpeg 9.0.2. Comparação SHA-256 de todas as entradas dos AARs oficiais: classes.jar, manifesto, regras ProGuard, metadados e ponte libplayer.so idênticos. Mudam libavcodec.so e libmpv.so nas três arquiteturas existentes: ARM64, ARMv7, x86_64.

Dois patches FFmpeg relacionados: chamadas MediaCodec com timeout zero, espera implementada fora da chamada com pausas curtas; erro quando entrada e saída permanecem em EAGAIN por mais de três segundos. Permitem ao mpv sair de um bloqueio específico de hardware e tentar software. Os patches devem permanecer juntos e a atualização deve usar o AAR completo.

A correção AudioTrack de 2026.10.1 permanece. O upstream corrigiu a atribuição anterior: essa correção reduz CPU durante underrun; não corrigia o congelamento do primeiro fotograma. 2026.10.2 não contém uma nova correção HLS ou de áudio, nem altera o caminho direto Media3/ExoPlayer.

Recomendação: atualização isolada para validação. Riscos: polling do descodificador, fallback de software perante backpressure, consumo/temperatura em vídeo exigente, compatibilidade entre saída direta mediacodec_embed e saída GPU durante fallback. Evidência publicada num TCL/Realtek Android 14 não valida automaticamente smartphones, Xiaomi, Thomson ou Mortal.

Fontes: [comparação](https://github.com/ahXN00/OwnTV_libmpv/compare/v2026.10.1...v2026.10.2), [correção e ensaios](https://github.com/ahXN00/OwnTV_libmpv/commit/a435770a88877b871f6f87e6f4d353b64c3f4eb9), [correção da atribuição AudioTrack](https://github.com/ahXN00/OwnTV_libmpv/commit/03ff1f03ea52cc69ceddf2ff7f50e90ac30b7921).

## Core 1.0.65: seleção

| Alteração | Comparação e decisão |
|---|---|
| Classificação AVC/H.264 e hardware de diferidos | Adaptar normalização de nomes de codecs e recuperação/superfície. Não forçar software nem copiar políticas globais de motor. |
| Retry de diferido preserva isArchive | Ausente no reload local. Portar a identidade do arquivo; avaliar posição válida por fornecedor. O upstream reinicia arquivos em zero, pelo que não promete retoma transparente. |
| BackgroundRestore preserva arquivo | Correção defensiva, mas estes métodos não têm chamadas na Mobile atual. Não atribuir-lhe impacto atual sem integrar o caminho. |
| Recuperação de diferido sem imagem e outro motor | Reavaliada; continua dependente de decisão do utilizador. Respeitar motor exclusivo e tentativa limitada. |
| Reconexão, DNS e prazo de resposta HTTP | Adaptar seletivamente. Preservar budgets, HLS estrito, proprietário e geração. Não copiar callbacks sem identidade. |
| Aviso de áudio em preview silenciado | Conferir mute deliberado. Política local já usa formato real da saída AudioTrack e tem proteções úteis; não substituir globalmente. |
| Recuperação de saída PCM já estéreo | Acrescentar apenas uma reconstrução limitada se demonstrado necessário; não fixar estéreo nem remover passthrough deliberado. |
| Master playlist HLS na gravação | Já existe suporte local substancial: masters, áudio separado, ranges, fMP4, retries e integridade. Não substituir o gravador. |
| Gravação HLS AES-128 | Ausente: local rejeita streams encriptados. Portar chaves, IV e desencriptação para pipeline local. Não é suporte DRM/SAMPLE-AES. |
| AutoRefresh partilhado | Coordenador e consumo das opções ausentes localmente. Integrar com ciclo de vida/WorkManager; não prejudicar reprodução ou gravação. |
| Sincronização de fontes restauradas | Adaptar enqueue das fontes restauradas antes de comunicar disponibilidade da lista. Preservar backup streaming e remapeamento local. |
| Agendamento por dia/hora sem EPG | Novidade útil; funciona sobre entidades existentes. Adaptar UI, DST, fuso, intervalos noturnos, conflitos e espaço. |
| Pesquisa por título de programa | Novidade útil; adaptar ao perfil/fontes/canais visíveis. Upstream limita horizonte e resultados; não é pesquisa de todo arquivo histórico. |
| Stalker Device conflict | Condicional a uso Stalker; retry controlado com modelo e identidade, sem expor resposta do portal. |
| Filmes, séries, favoritos, histórico | Fora do âmbito pretendido. Não reintroduzir. |

## Riscos que o council exige resolver

1. ResponseDeadline aplica-se depois do primeiro fotograma, mas callbacks upstream partilham contadores sem identidade da sintonia. Capturar e verificar proprietário/geração antes de alterar o canal atual.
2. BoundedDns deduplica consultas do mesmo host, mas usa executor e mapas sem limites globais/expiração explícitos. Adaptar concorrência e cache, manter DNS/DoH escolhido. Não resolve um HTTP 403 do fornecedor.
3. AES-128 exige limites de segmento/chaves, validade de IV, rotação de chaves mesmo com URI repetida, cancelamento, headers e redirects; testar ranges/fMP4/init e manter rejeição dos formatos não suportados.
4. AutoRefresh não deve assumir que só Live representa utilização ativa: considerar diferido, gravações e recursos da fonte. Evitar agendamento duplicado e atualização pesada durante reprodução.
5. Não substituir OwnTVPlayer, LivePreviewEngine, BackupManager, RecordingEngine ou migrations completos: isso removeria funcionalidades locais, áudio FFmpeg ExoPlayer, volume interno a 100%, regras por canal e proteções de passagem.
6. Não impor mediacodec-copy/GPU ou queueing síncrono a todos os smartphones por resultados num TCL. Conservar saída/queueing locais e avaliar exceções por dispositivo. A correção de passthrough upstream já é superada pela observação local do formato AudioTrack com validação de época; a recuperação PCM deve manter o limite de tentativas local.

## Ordem proposta, ainda não aplicada

1. Atualização isolada libmpv, acompanhada de ensaios do caminho nativo/superfície; sem merge global Core.
2. Correções seletivas codec/arquivo/superfície e áudio, com proteções de identidade anteriores preservadas.
3. Reconexão/DNS/HTTP, mantendo limites e callbacks vinculados à sintonia.
4. AES-128 no gravador local, com testes de integridade, cancelamento, chaves e limites.
5. AutoRefresh e listas restauradas; depois agendamento manual e pesquisa de programas.

Aceitação: comparar nas mesmas fontes/dispositivos; abrir/recuar/repetir diferidos, zapping A/B/C, pausa/stop/background, Exo exclusivo e mpv exclusivo; verificar hardware/software, CPU/temperatura, volume100%, HLS estrito, gravações simples e multipart, quotas e restauro. Build/lint/unidade são necessários numa implementação, mas não substituem reprodução em Android.

Fontes Core: [release](https://github.com/ahXN00/OwnTV_Core/releases/tag/core-1.0.65), [diff 1.0.64–1.0.65](https://github.com/ahXN00/OwnTV_Core/compare/core-1.0.64...core-1.0.65). Pendência anterior: ATUALIZACOES_OWNCORE_PENDENTES_DE_IMPLEMENTACAO.md.
