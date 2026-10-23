# Stage adaptado à Mobile de canais

Data: 19/10/2026. Autorização: «implementar o novo visual Stage mas adaptar ao que eu tenho pois eu não uso sports, nem filmes».

## Resultado

Stage passa a ser a apresentação normal da Mobile, usando os ecrãs e os componentes existentes. A implementação adapta a paleta, cantos, hierarquia de texto, seleção, navegação e menus do Stage da Mobile1.1.0. Não importa a sua arquitetura de navegação nem os ecrãs de catálogo.

O âmbito é **TV em direto, versões de canais, Guia TV, diferidos, gravações, pesquisa e definições**. A barra mantém TV em direto/Guia/Mais. Gravações e definições conservam os acessos atuais. Não foram acrescentados desporto, filmes/séries, favoritos, histórico, meteorologia, OpenSubtitles ou novas chamadas de rede.

## Aspeto e acessibilidade

- Neutros Stage em tema escuro/claro; a cor de destaque, fonte, zoom e tamanho de texto continuam a respeitar as preferências guardadas.
- Canal atual: fundo com a cor de destaque, contorno e faixa lateral; texto mantém a cor contrastante do tema.
- Foco de teclado/comando: contorno próprio, separado do estado de canal atual. Sem alterações aos tempos de pressão nem aos comandos de reprodução.
- Listas e menus com cantos de16dp; navegação26dp; folhas28dp; campos14dp e botões16dp.
- Títulos com maior hierarquia, linhas com texto flexível até duas linhas e categorias com alvo tátil mínimo48dp. Canal com mínimo66dp, podendo crescer com fontes maiores.
- Cabeçalho sem o painel pesado anterior e página sem uma moldura interior adicional. Navegação e categorias selecionadas usam o destaque Stage.
- Grelha EPG mantém a paginação, intervalos temporais e cliques da implementação anterior, com delimitação visual dos programas.

## Custo de desenho e compatibilidade

O fundo normal é um gradiente estático em cache por tamanho/tema. Sem imagem escolhida, a composição termina antes de criar a escada de texturas/desfocagem do Glass anterior. As superfícies Stage usam preenchimento/contorno; não é acrescentado blur, sombra por cartão, animação de chegada ou polling.

Uma imagem de fundo explicitamente configurada conserva o percurso Glass já existente e os seus controlos. As sobreposições que fornecem a sua própria cor mantêm esse contrato. Animações continuam desligadas.

Não se alteraram o Core privado, schema, backup, HLS, motores, handover, gravação, Cast, controlo do volume ou bibliotecas. O volume interno continua100%. Não foi gerado APK. As alterações anteriores foram preservadas; snapshot anterior em `%TEMP%/owntv-mobile-before-stage-20261019`.

## Pré-visualizações

Android Studio: abrir `app/src/debug/java/tv/own/owntv/mobile/ui/stage/StagePreview.kt` em Split/Design. Inclui telefone360dp, tema claro com fonte140% e tablet800dp. Usa os componentes reais e dados fictícios locais, sem ligar ao fornecedor. Estas composições de demonstração não entram no APK release.

## Validação

**BUILD SUCCESSFUL** na execução final. Compilação Kotlin standardRelease/debug, testes e lintVitalStandardRelease aprovados. Relatório lint vital: `No issues found.` Inventário i18n aprovado (baselineMobile0; removida apenas a classificação técnica obsoleta rowPress) e git diff --check sem erros.

- Mobile: **119 testes**, incluindo3 novos testes de contraste Stage; zero falhas/erros/ignorados.
- Core privado:1.180 e reprodutor459 continuam aprovados, reaproveitados pelo Gradle por não haver alterações nestes módulos. Total de resultados XML: **1.758**.
- Testes verificam contraste de texto principal/secundário nos painéis claros/escuros, texto no canal selecionado, cores predefinidas e preservação do destaque escolhido.
- Pré-visualizações debug compiladas, mas não renderizadas/inspecionadas num dispositivo. Não se executou teste físico de toque, teclado, rotação ou TalkBack, nem benchmark CPU/GPU/RAM.
- Comparação do diff do Core privado antes/depois confirmou igualdade. Não foi alterada a base de dados nem o motor.

Registo: [STAGE_MOBILE_20261019.json](validacao/STAGE_MOBILE_20261019.json).

## Teste no dispositivo

1. Percorrer canais e versões; verificar contraste do canal atual e do foco com teclado/comando, quando usado.
2. Abrir menus, trocar categoria e confirmar que nomes extensos permanecem utilizáveis.
3. Tema claro/escuro, fonte grande, rotação e tablet/split-screen.
4. Abrir Guia, diferido e gravação e confirmar as mesmas ações/comandos de antes.
5. Modo simples com barra/categorias ocultas; confirmar que Mais/definições continuam acessíveis.

A implementação reduz o trabalho decorativo do percurso sem wallpaper; não existe benchmark físico para atribuir uma percentagem de melhoria.
