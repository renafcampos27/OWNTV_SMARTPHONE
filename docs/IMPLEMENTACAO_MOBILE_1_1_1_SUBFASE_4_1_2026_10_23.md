# Mobile 1.1.1 — subfase 4.1: seleção e recuperação de áudio ExoPlayer

Data: 23/10/2026. Autorização: «avança com a subfase 4.1», seguida de «continua». Estado: implementada e validada automaticamente; reprodução física pendente.

## Comportamento implementado

| Escolha | Comportamento |
|---|---|
| Automático | MediaCodec mantém prioridade nos formatos para os quais anuncia um decoder. FFmpeg continua disponível automaticamente quando não existe decoder nativo para o MIME. Uma declaração de capacidades não é tratada como prova de funcionamento real. |
| Software | FFmpeg tem prioridade nos formatos de áudio que consegue descodificar, mesmo que o Android anuncie MediaCodec. Se FFmpeg não estiver disponível/não suportar o formato, os decoders do sistema continuam como alternativa. A opção é uma preferência; não promete suporte de qualquer codec. |
| Por canal | Valor específico sobrepõe-se ao global, incluindo Automático explícito sobre Software global. Herdar mantém a preferência global. O valor também chega aos diferidos/recuo do mesmo canal quando usam o percurso Exo. |
| Falha real de decoder de áudio | Uma tentativa de FFmpeg por seleção/item, quando o formato é compatível, não protegido e a reprodução foi pedida. Reconstruções e reconexões do mesmo item mantêm o orçamento; uma seleção nova começa sem a preferência automática anterior. |

A ordenação dos renderers por si só não garantia esta política: Media3 também compara as capacidades anunciadas, podendo escolher FFmpeg acima de um MediaCodec que apenas anuncia FORMAT_EXCEEDS_CAPABILITIES. `FfmpegAudio.takes` passa a resolver a participação por instância: em Automático reserva o MIME anunciado para o decoder nativo; em Software/resgate permite FFmpeg. A verificação da lista de decoders é cacheada por MIME. O gate de DRM e as verificações de PCM do renderer mantêm-se.

Referências técnicas: [MappingTrackSelector Media3 1.11.1](https://github.com/androidx/media/blob/1.11.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/trackselection/MappingTrackSelector.java), [ExoPlaybackException](https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlaybackException) e [fallback de inicialização](https://developer.android.com/reference/androidx/media3/exoplayer/DefaultRenderersFactory#setEnableDecoderFallback(boolean)). A recuperação explícita desta fase cobre uma falha identificada do decoder de áudio; não assume que o fallback de inicialização do MediaCodec substitui automaticamente um renderer por outro após uma falha em execução.

## Âmbito da recuperação

`AudioDecodeRecovery` pertence à seleção Live ou ao `ItemState` do leitor principal. Não escreve em preferências nem aprende por servidor. O decoder de áudio pode mudar sem ativar a recuperação de vídeo por software.

- O Live Exo usa a reconstrução já existente, conservando configurações por canal, pedidos/headers, escolha de motor, posição quando aplicável, intenção de pausa/reprodução e identidade HTTP. A antiga origem é libertada e a nova preparação continua a respeitar `SourceDrain`.
- O Exo de diferidos/gravações conserva o orçamento no item e usa a transição existente com libertação do player e superfície nova. O callback verifica geração e URL atuais; um item ultrapassado não pode retomar o anterior.
- A faixa selecionada é transportada por identidade de idioma/etiqueta/ordinal compatível. Uma emissão parcial de faixas não elimina imediatamente a seleção ainda por encontrar. Uma seleção manual posterior tem prioridade.
- Falhas de rede, HTTP 403, DRM, sink/AudioTrack e vídeo não consomem esta tentativa de decoder de áudio. Os seus percursos específicos conservam-se. Um erro do próprio FFmpeg não consome um novo resgate para o mesmo decoder nem aciona a tentativa de descodificação de vídeo por software. Os restantes percursos de reconexão conservam os orçamentos já existentes.
- Se a tentativa de áudio por software falhar, o erro segue o percurso terminal/fallback já autorizado pela escolha de motor. Não se ultrapassa ExoPlayer exclusivo nem se introduz mudança de HLS para TS.

O mpv já descodifica áudio através de FFmpeg; esta escolha refere-se ao percurso ExoPlayer. Não altera o decoder de vídeo mpv nem força um novo motor.

## Definições, foco e backup

Mantém-se o interruptor **Descodificação de áudio por software** em **Direto: compatibilidade e versões**, agora com explicação: desligado é Automático; ligado prefere FFmpeg. Na configuração por canal, o rótulo incorreto «Hardware» para false foi substituído por **Automático**. Permanecem Herdar e Software.

Os ExoPlayers usam atributos de reprodução média/conteúdo de vídeo coerentes com o `PlaybackSession`. Não pedem foco áudio adicional: esse proprietário permanece inalterado. Não foram repostos controlos de áudio no leitor.

Continuam os mesmos campos persistentes `software_audio` e `channel_playback_configs`, já cobertos pelo backup. Sem novo interruptor mestre, schema, migração ou exclusão do backup. `SettingsRepository` e `ChannelPlaybackConfig` ficaram idênticos por hash.

**Volume interno permanece em 100%; o sistema controla o volume.**

## Validação automática

**BUILD SUCCESSFUL**, execução completa em 43 s. Compilação release/debug e consumidores, testes JVM e `lintVitalStandardRelease` aprovados. Lint: `No issues found.`

| Suíte | Testes | Falhas / erros / ignorados |
|---|---:|---:|
| Mobile | 119 | 0 |
| Core privado | 1.193 | 0 |
| player-core | 508 | 0 |
| Total | **1.820** | **0** |

14 testes novos: prioridades Automático/Software com AAC/MP2/AC3 simulados, ausência de decoder/biblioteca, orçamento por item e reset, exclusão de vídeo/DRM/rede/saída, erro FFmpeg, faixa selecionada e idioma preferido. Os testes existentes de ordenação de decoders, retenção, áudio, HTTP, superfícies, backup e estado também passaram. Os testes de política não executam codecs Android nem o JNI FFmpeg.

Inventários i18n: Mobile 0 baseline + 585 entradas técnicas; Core 310 baseline + 5.715 técnicas. Quatro novos literais técnicos revistos, sem aumento de dívida. Novo texto visível em recurso XML, seguindo o padrão português das definições personalizadas. `git diff --check` aprovado.

Evidência: [MOBILE_111_FASE4_1_20261023.json](validacao/MOBILE_111_FASE4_1_20261023.json). Log final: `%TEMP%/owntv-mobile-111-phase4-1-final-validation-20261023.log`.

## Preservação e rollback

Projeto: `C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main`, Core privado em `vendor/OwnTV_Core`. TV e Core externo não modificados. Schema 52, Media3 1.11.1 e libmpv 2026.10.4 mantidos; não houve atualização de binários nem alteração dos nomes JNI de FFmpeg.

Snapshot verificado: `C:/Users/renat/AppData/Local/Temp/owntv-mobile-before-phase4-1-111-20261023`, 533 entradas originais, inventário SHA256 e cópias dos ficheiros preexistentes modificados. As alterações anteriores não commitadas foram preservadas.

Ficheiros protegidos confirmados por hash: `PlaybackSession`, `PlaybackSettings`, `LiveTuneController`, `SourceDrain`, `PlaybackHttpCalls`, `NativeSurfaceBinding`, `VideoOutputReady`, `BufferUiSample`, `AudioTrackRetention`, `SettingsRepository`, `ChannelPlaybackConfig`; base de dados comparada com a cópia verificada da fase 3. Buffers, latência, retoma imediata, HLS/exceções e ganho de volume mantêm as políticas anteriores.

Sem APK, instalação, assinatura, commit ou publicação. Sem benchmark de CPU ou prova de compatibilidade física. Nenhum dispositivo ADB estava ligado.

## Aceitação no dispositivo

1. Em Automático, abrir canais AAC, MP2 e AC3 disponíveis e confirmar som/decoder nas informações de reprodução. Testar direto e diferido; a ausência de MediaCodec MP2 deve continuar a permitir FFmpeg.
2. Ativar Software e confirmar FFmpeg quando disponível. Alternar a opção global e testar uma exceção por canal, incluindo Automático por canal com Software global.
3. Num stream com falha real do decoder nativo, confirmar uma tentativa de áudio por software, sem ativar software de vídeo, sem ciclos de reconstrução e sem sessão antiga sobre o canal seguinte.
4. Com áudio em vários idiomas, mudar/reconstruir e verificar a faixa selecionada. Confirmar pausa/retoma, velocidade e posição nos diferidos/gravações.
5. Exportar/restaurar as configurações num teste controlado e confirmar as opções globais/por canal. Verificar que o volume continua a ser alterado pelo sistema.

## Subfases seguintes

| Subfase | Estado |
|---|---|
| 4.1 — seleção de áudio Automático/Software | Implementada; validação física pendente |
| 4.2 — identificação da saída efetiva | Pendente |
| 4.3 — sincronismo por saída e backup | Pendente |
| 4.4 — FPS/AFR | Pendente |
| 4.5 — preferência SDR opcional | Pendente |
| 4.6 — diagnóstico de perdas de fotogramas | Pendente |
| 4.7 — recuperação de degradação grave | Pendente; depende dos resultados da 4.6 |

Atualização de 23/10/2026: as subfases 4.2 a 4.7 foram implementadas após autorização conjunta. Ver [relatório e limites de validação](IMPLEMENTACAO_MOBILE_1_1_1_SUBFASES_4_2_A_4_7_2026_10_23.md).
