# Adaptação da variante personalizada para OwnTV Mobile

Data: 04/10/2026.

## Âmbito e decisão do conselho

O utilizador autorizou adaptar funcionalidades, modos e definições da variante TV para o projeto mobile, com análise por agentes especializados antes da implementação. A pasta OwnTV-main não pode ser alterada.

As revisões de definições, reprodução e interface/dados recomendaram preservar Material3, teclado Android, layouts de telefone/tablet, serviço multimédia, Cast, gestão de dados móveis e seletor de documentos. Transportar a lógica e a persistência; não copiar os ecrãs D-pad da TV.

## Isolamento

O projeto mobile usa exclusivamente `vendor/OwnTV_Core`, cópia independente do Core personalizado. O `includeBuild` não usa a propriedade global `owntv.corePath`. Não modificar nem gerar ficheiros no Core partilhado usado pela TV. O ficheiro de verificação de integridade inicial cobre 488 ficheiros da TV e 893 do Core original, excluindo caches, Git e artefactos de compilação.

Original do projeto mobile guardado em `C:\Users\renat\Downloads\OwnTV_Mobile-main\OwnTV_Mobile-original-20261004.zip`.

## Adaptações

- Definições personalizadas de HLS, áudio, buffer, descodificação e reprodução por canal.
- Modos simples, ocultação de categorias/navegação e recuperação entre versões com tempo configurável.
- Grupos e prioridade de versões com exclusão dos canais ocultos, adaptados a listas e seletores táteis.
- Guia, diferidos e correção separada da hora do pedido; gravações e programas antigos permanecem disponíveis.
- Limites de armazenamento e destino das gravações.
- Backup seletivo do Core personalizado mais modos simples da app, sem transportar permissões de autoarranque da TV.
- Volume interno a 100%; gestos locais controlam o volume do Android e Cast mantém o controlo do recetor.
- Simplificação das entradas de filmes/séries/favoritos/histórico/sincronização conforme a variante TV, sem apagar os dados existentes.

Não importar autoarranque por Acessibilidade para smartphone. PiP permanece excluído. O lote 2 de recuperação dos diferidos sem imagem permanece adiado; a cópia usada é a variante personalizada, não uma substituição pelo Core original 1.0.62.

## Onde encontrar as opções

- Definições → Reprodução → Direto: compatibilidade e versões: HLS, áudio por software, fila do descodificador, reserva geral/por lista, correção do pedido diferido, modo simples e recuperação entre versões.
- Definições → Reprodução → Definições por canal: escolher lista, procurar canal e definir exceções; os valores não alterados herdam as definições gerais.
- Detalhes do canal → versões: escolher, associar/desassociar e ordenar variantes. Guardar uma ordem manual ativa a prioridade. As categorias e canais ocultos ficam excluídos da seleção automática.
- Mais → Gravações: gravações e programas antigos descarregados. Definições de gravação: armazenamento interno ou volume removível exposto pelo Android como diretório da app.
- Backup: configurações personalizadas e modos simples incluídos. Definições dependentes do dispositivo são protegidas ao restaurar noutro equipamento, salvo escolha explícita.

## Dados e compatibilidade

Apenas o snapshot mobile passa à base de dados 51. A migração reúne os campos de metadados do EPG e os lembretes da base mobile original com os campos de reprodução por lista da variante personalizada. A base TV permanece na versão anterior. Há testes para as duas origens; não se faz substituição destrutiva da base de dados.

O projeto mantém mínimo Android 8 (API 26). A compilação standard suporta ARM de 32 e 64 bits. A compatibilidade física de codecs, armazenamento removível e serviços depende do dispositivo e ainda precisa de ensaio. O número de versão de desenvolvimento do projeto não foi alterado nesta adaptação.

As funcionalidades upstream sem implementação correspondente no Core personalizado (ex.: modos de áudio noturno/nivelamento e timeshift local) não são apresentadas como opções operacionais. Ficheiros e dados antigos não foram apagados. O destino de gravações usa diretórios próprios da app; não promete gravação arbitrária em qualquer pasta SAF.

## Revisão final do conselho

Corrigidos: retorno do zapping após recuperação para uma variante com ID diferente; respeito pelas categorias ocultas; identidade original do fornecedor apesar dos nomes personalizados; arranque com canal fixo sem aceitar IDs reutilizados para outros canais; ordem manual com prioridade efetiva.

## Validação

Concluída a validação de compilação da variante standardDebug em 04/10/2026:

- `:app:compileStandardDebugKotlin`: sucesso, incluindo o último ajuste de navegação.
- `:app:testStandardDebugUnitTest`: 48 testes, zero falhas/erros/ignorados.
- `:OwnTV_Core:core:testDebugUnitTest`: 1 049 testes, zero falhas/erros/ignorados.
- `:OwnTV_Core:player-core:testDebugUnitTest`: 417 testes, zero falhas/erros/ignorados.
- Total: 1 514 testes JVM. Incluem backup/configurações, variantes de canais, recuperação e migrações de base de dados.
- `:OwnTV_Core:core:compileDebugAndroidTestKotlin`: sucesso. Os testes Android/Room foram compilados, não executados em dispositivo.
- `:app:mergeStandardDebugNativeLibs`: sucesso; mpv e FFmpeg JNI presentes para armeabi-v7a e arm64-v8a. Isto confirma empacotamento intermédio, não descodificação física.
- Inventários de literais i18n da app e do snapshot Core: aprovados sem aumentar a baseline histórica.
- Verificação SQLite independente: origens mobile 47 e personalizada 50 preservam os dados verificados na união 51.
- Integridade protegida: 488 ficheiros da TV e 893 do Core partilhado, sem ficheiros alterados/removidos/adicionados no âmbito verificado.

A última execução Gradle terminou com `BUILD SUCCESSFUL`; o aviso novo de opt-in de coroutines foi corrigido. Os ficheiros JSON junto deste relatório guardam os resultados dos testes e da integridade.

Não há testes físicos de reprodução, codecs, Cast, foco, permissões de armazenamento, rotação ou benchmarks nesta adaptação. Antes de distribuir, testar no smartphone o arranque do canal fixo, zapping A→B→C→A, versões/grupos, diferido, restauro de backup, gravação e volume do sistema. Não confundir compilação e testes JVM com confirmação de estabilidade do fornecedor IPTV.

Não foi gerado/instalado APK, nem efetuado commit/push. Abrir no Android Studio a pasta de projeto `C:\Users\renat\Downloads\OwnTV_Mobile-main\OwnTV_Mobile-main`, que contém `settings.gradle.kts`. O Core desta versão está dentro de `vendor/OwnTV_Core`; não voltar a apontar o Gradle para o Core partilhado da TV.

## Manutenção futura

Novidades do Core não entram automaticamente nesta variante: comparar o novo código, o snapshot mobile e as pendências. Consultar `ATUALIZACOES_OWNCORE_PENDENTES_DE_IMPLEMENTACAO.md` antes de integrar uma nova publicação. O número upstream no catálogo é uma referência; as dependências Core/player-core são substituídas pelo snapshot local.

## Assinatura release para uso pessoal

Por pedido explícito do utilizador, a variante release mobile usa `signingConfigs.getByName("debug")`: a chave de debug padrão local do Android, sem novas credenciais release. A configuração de assinatura externa foi removida deste projeto mobile. Isto não altera a assinatura/configuração da versão TV.

Gerar no Android Studio escolhendo `standardRelease` em Build Variants e Build → Generate App Bundles or APKs → Generate APKs. Não é necessário o assistente Generate Signed APK com criação de uma nova chave.

A chave local está em `C:\Users\renat\.android\debug.keystore`. A configuração padrão tem alias `androiddebugkey` e palavras-passe `android`. As chaves de debug geradas em computadores diferentes não são necessariamente iguais: guardar o ficheiro fora do Git para continuar a atualizar instalações existentes. Esta assinatura serve o uso pessoal por instalação direta; não é uma chave de publicação para Google Play.

## Simplificação dos botões do reprodutor

Por pedido do utilizador, removidos do HUD os botões de seleção de faixa de áudio, Apenas áudio, Temporizador e Legendas. Os controlos são excluídos das duas disposições (retrato/paisagem), evitando espaços vazios. Removidas também as entradas Apenas áudio/Temporizador do menu do mini-reprodutor e o atalho de temporizador do fundo sem vídeo. O HUD deixa de observar o temporizador. A reprodução de áudio e a renderização de legendas existentes não são alteradas por esta remoção visual.

Validação desta simplificação: :app:compileStandardDebugKotlin aprovado (BUILD SUCCESSFUL). Sem geração de APK e sem ensaio visual em dispositivo.
