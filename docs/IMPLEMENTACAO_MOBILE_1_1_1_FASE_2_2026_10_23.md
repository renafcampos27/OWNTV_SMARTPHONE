# Mobile 1.1.1 — fase 2: libmpv e diferidos em hardware

Data: 23/10/2026. Autorização: «avança com a segunda fase».

## Implementado

### libmpv 2026.10.4

O catálogo do Core privado passa de `2026.10.3` para **2026.10.4**. Não foi substituído o Core pela versão upstream 1.0.69 nem alterado o Media3 1.11.1. O binário publicado conserva os commits base de mpv/FFmpeg e acrescenta as correções nativas descritas no [changelog](https://github.com/ahXN00/OwnTV_libmpv/blob/v2026.10.4/CHANGELOG.md): HTTP chunked completo no HLS, HEVC sem extradata, configuração adaptativa H.264, espera por keyframe em arquivos e estimativa de FPS quando falta a declaração.

Estas correções nativas beneficiam o percurso mpv. Não equivalem a trocar o HLS do ExoPlayer pelo FFmpeg, nem garantem que um problema específico do fornecedor desapareça.

### Abertura de diferidos

`MpvProbeProfile` resolve os parâmetros nativos por carregamento:

- Diferido em hardware: probe completo e `vd-lavc-o=wait_keyframe=1`, para evitar entregar ao MediaCodec os fotogramas iniciais cujas referências ainda não chegaram.
- Direto, gravação normal ou descodificação por software: escreve explicitamente a opção vazia, para não herdar a espera do arquivo anterior. A transição para software por recuperação também limpa a opção MediaCodec.
- HLS conserva o probe completo; o probe reduzido fica limitado a **raw TS em direto**, sem pedido de probe completo. Um ficheiro/gravação TS finito não é tratado como zapping ao vivo.
- A opção nativa espera no máximo 300 pacotes antes de voltar a alimentar o decoder; não foi acrescentada uma espera fixa em segundos nem uma nova política de buffer.

O hardware só é usado quando a preferência existente o permite e o decoder consegue abrir o conteúdo. O software continua disponível como recuperação finita do item atual. As escolhas manuais e o motor exclusivo conservam as proteções existentes.

### Recuperação isolada ao programa atual

Retirados `ArchiveDecodeStore`, a sua injeção no arranque/definições e o mapa automático de servidores em `LiveStreamQuirks`. Nem mpv nem Exo passam agora a marcar um painel inteiro para software após uma falha de imagem de um arquivo.

A recuperação continua em `ItemState`: uma repetição do mesmo item conserva a decisão e o orçamento de tentativas; uma seleção nova recebe estado novo e segue a preferência do utilizador. Foram preservadas as identidades/gerações, os pedidos de arquivo e a retenção de faixas existentes.

**Correção ao diagnóstico anterior:** o `ArchiveDecodeStore` local inspecionado não tinha TTL. Os 14 dias eram uma política upstream referida na comparação; na nossa cópia a aprendizagem só era removida manualmente. Ambas deixam de ser relevantes neste percurso após esta fase.

O ficheiro antigo de aprendizagem que possa existir no aparelho deixa de ser lido/escrito; não é necessário apagar os dados da app para o ignorar. Não foi feita uma limpeza automática dos ficheiros do utilizador. O botão «Esquecer correções aprendidas», cuja ação local apenas limpava este armazenamento, e a sua entrada de pesquisa foram retirados. Os resets de escolhas manuais de motor/zoom/áudio e as opções por canal mantêm-se.

## Validação automática e do artefacto

**BUILD SUCCESSFUL** na primeira execução (2 min 42 s) e na execução final após o ajuste de software (41 s). Kotlin release/debug, integração dos consumidores, merge das bibliotecas nativas e `lintVitalStandardRelease` aprovados. Lint: `No issues found.` Inventários i18n e `git diff --check` aprovados; sem aumento de dívida de tradução.

| Suíte | Testes | Falhas/erros/ignorados |
|---|---:|---:|
| Mobile | 119 | 0 |
| Core privado | 1.192 | 0 |
| player-core | 464 | 0 |
| Total | **1.775** | **0** |

Os testes Core correram na primeira execução e foram reutilizados pelo Gradle na final, pois o último ajuste só alterou o player. Foram acrescentados 7 testes (perfis de probe/keyframe, transição arquivo→direto, software, gravações TS e âmbito do estado de recuperação) e retirados 2 testes que exigiam a aprendizagem por servidor agora eliminada. As restantes suítes do player incluem HLS estrito, cancelamento HTTP, passagem de canais, estado e recuperação.

Verificações estáticas do AAR oficial:

- SHA256 confirmado contra o checksum Maven publicado: `30da5e483342ee7c44ac57a694050279a1a69a5a0d55876ebf94cfdd90fbbfe4`.
- 30 bibliotecas, nas arquiteturas arm64-v8a, armeabi-v7a e x86_64; tipo de máquina ELF correto, build-id presente, alinhamento de pelo menos 16 KiB nas bibliotecas de 64 bits e sem dependência libvulkan.
- JNI de áudio presente nas três arquiteturas e ligado ao mesmo avcodec/avutil/swresample do artefacto; símbolos JNI Java anteriores preservados. Opção nativa wait_keyframe presente.
- Conjunto e bytecode das classes Java do wrapper idênticos aos do AAR 2026.10.3.
- As 30 bibliotecas do merge release da app coincidem por hash com as do novo AAR.

Estas são verificações do artefacto, não execução de codecs nativos nem repetição do contrato upstream que depende das tabelas geradas no ambiente de compilação deles. Não foi gerado APK.

Evidência: [MOBILE_111_FASE2_20261023.json](validacao/MOBILE_111_FASE2_20261023.json) e [verificação nativa](validacao/LIBMPV_20261004_FASE2_STATIC.json). Log final: `%TEMP%/owntv-mobile-111-phase2-validation-final-20261023.log`.

## Preservação e rollback

- Snapshot anterior: `C:\Users\renat\AppData\Local\Temp\owntv-mobile-before-phase2-111-20261023`, com inventário/hash e cópias dos ficheiros, incluindo os manifests i18n e o catálogo.
- Os 13 ficheiros de código preexistentes alterados/removidos têm cópia verificada por SHA256. O inventário inclui as alterações anteriores ainda não commitadas.
- Schema privado 52, base de dados, alterações da fase 1 e LivePreviewEngine Exo preservados por comparação com o snapshot.
- Sem alteração das reservas/pre-buffer/latência ou retoma imediata do direto, do controlo de pedidos HTTP, do ganho interno de 100%, de Stage ou da app TV/Core externo.
- Sem instalação, assinatura, commit ou publicação. O AAR anterior mantém-se disponível no cache para rollback pela versão do catálogo.

## Aceitação física pendente

Não havia dispositivo ADB ligado. Testar após gerar/instalar o APK:

1. Abrir vários diferidos H.264/HEVC e confirmar o decoder efetivo; alternar para direto e verificar que não herdou a espera de arquivo.
2. Repetir com software escolhido manualmente e com cada modo de motor exclusivo; confirmar que a preferência não é substituída.
3. Num arquivo com falha real, verificar recuperação limitada; abrir outro do mesmo servidor e confirmar que não herdou software automaticamente.
4. Testar HLS no mpv, incluindo uma origem com respostas chunked, e comparar diretos saudáveis nos dois motores.
5. Zapping repetido, regresso lista/fullscreen, áudio e legendas. Ensaiar arquiteturas ARMv7/ARM64 reais quando disponíveis.

Não foi medido ganho de CPU, tempo de arranque ou estabilidade em fornecedor real nesta execução.

## Fases seguintes

Fase 3: gestão das superfícies e operações do player, erro terminal e custo de diagnóstico/JNI. Fase 4: opções avançadas de áudio/imagem. Ambas continuam por implementar conforme a [análise](ANALISE_MOBILE_1_1_1_CORE_1_0_69_2026_10_23.md).
