# OwnTV Mobile — implementações gerais 1 a 6

Data: 07/10/2026. Âmbito: OwnTV Mobile e o seu Core independente em vendor/OwnTV_Core. Sem alterações desta tarefa na OwnTV-main ou no Core partilhado. Sem geração de APK.

## 1. Trabalho desnecessário

- Retirados os coletores de favoritos/histórico do ecrã Mais, favoritos das listas Live/Guia e favoritos/canal anterior do leitor.
- Consulta EPG suspensa, cancelada pelo collectLatest da lista. Publicação condicionada ao contexto atual.
- Cache exterior limitado a 512 canais; validade até ao fim do programa/TTL do Core; ausência guardada durante 30 segundos. Invalidação por contexto e revisão do leitor EPG. Atualização dos canais visíveis a cada 30 segundos, apenas com o ecrã STARTED. Nenhuma nova consulta de rede por canal.
- Relógios visuais respeitam o lifecycle. Tempos do diferido observados dentro do HUD, sem parar a lógica funcional do timeshift.
- Strings JSON comparadas antes de interpretar personalizações e configurações por canal.
- Permissões/categorias carregadas uma vez por pedido de versões. Referências manuais e canais personalizados resolvidos em lotes de até 400 parâmetros.
- Sampler detalhado sem despertares periódicos quando está desativado. Watchdogs e diagnóstico essencial preservados.
- MediaSession em direto evita republicar estado por alterações irrelevantes de posição; diferidos mantêm posição.
- SHA-256 calculado durante a transferência dos segmentos HLS; EOF, comprimento, fsync, movimentos atómicos e verificações na recuperação preservados. Progresso de captura e ciclo de vida atualizados num único UPDATE Room.

## 2. Definições por canal

Nomes legíveis do motor/formato, segundos e milissegundos. Edição numérica com −/+, limites e opção Herdar, em vez de centenas de opções. Valores herdados e origem geral/lista visíveis; atraso de áudio guardado no leitor também considerado. Flags HLS com três estados, incluindo herança. Filtro de canais personalizados com resolução em lotes, independente dos primeiros 100 resultados da pesquisa normal.

Pesquisa/lista e identificação do canal editado guardadas no estado da interface; rascunho serializado para restauro. Editor espera pela primeira configuração real. Voltar/Cancelar pede guardar ou descartar alterações; a seta da barra usa o dispatcher de Back. Falha ao guardar mantém a edição e apresenta mensagem; não fecha como se tivesse guardado.

## 3. Estados distintos

Pesquisa apresenta carregamento, resultado vazio, ausência de listas e falha com repetição. Gravação das configurações apresenta progresso e falha. Este lote aplica os estados novos à personalização por canal; não redesenha todos os estados já existentes nas restantes páginas.

## 4. Backup

Acesso em Definições, reutilizando a mesma página de Mais. Data da última cópia concluída. Resumo de perfis/secções selecionados e indicação explícita da inclusão/exclusão das palavras-passe. Explicação dos dados incluídos e dos ficheiros não copiados: vídeos, gravações e conteúdo dos M3U locais. Preservado o formato de backup e o restauro com atualização das listas.

## 5. Organização

Definições habituais visíveis por predefinição; motor/reprodução, rede e conteúdo avançado através de Opções avançadas. Pesquisa continua a encontrar as opções avançadas mesmo com o grupo oculto. Separação visual não altera configurações existentes.

## 6. Limpeza

Eliminados FavoritesScreen.kt e MoreCountsViewModel.kt e a respetiva ligação DI. Removidas rotas de favoritos, histórico e sincronização local de Mais, callbacks mortos do HUD e parâmetros de navegação associados. Comentários e inventário de literais técnicos atualizados. Gerador de Baseline Profile ajustado ao shell de três destinos; os perfis gerados só podem ser renovados com execução em dispositivo.

Preservados os dados, schemas e compatibilidade de backup; VodTuner usado por gravações/downloads; componentes de sincronização usados pelo assistente inicial; seleção/renderização automática de áudio/legendas. Não foi removida uma biblioteca sem prova de ausência de consumidores; R8 e redução de recursos já estavam ativos.

## Validação

Compilação Kotlin debug, testes unitários dos três módulos e processamento de recursos release: BUILD SUCCESSFUL. Teste novo compara SHA-256 da transferência com o ficheiro e confirma restauro do journal. Contagens e inventário em IMPLEMENTACOES_GERAIS_MOBILE_2026-10-07.json. Sem APK e sem benchmark/validação visual num smartphone ou box física. Não há percentagens de ganho medidas.

## Teste recomendado no dispositivo

1. Percorrer rapidamente a lista; esperar pela informação EPG; regressar após mudança de programa.
2. Abrir Opções avançadas → reprodução por canal; procurar, filtrar personalizados, editar, herdar, cancelar e guardar.
3. Sair com rascunho pela seta e pelo gesto Voltar; verificar a confirmação.
4. Exportar com/sem palavra-passe e restaurar; confirmar fontes e configurações.
5. Reproduzir direto/diferido, ocultar/mostrar HUD, recuar e regressar ao direto.
6. Gravar, terminar e recuperar uma captura interrompida. Medir CPU/fluidez no dispositivo antes de atribuir ganhos.
