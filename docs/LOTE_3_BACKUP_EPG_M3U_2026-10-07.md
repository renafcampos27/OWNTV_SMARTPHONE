# Lote 3 — Backup, EPG e sincronização M3U

Implementação na OwnTV Mobile, incluindo o Core incorporado em `vendor/OwnTV_Core`. Os projetos externos OwnTV-main e OwnTV_Core não foram alterados neste lote. Não foi gerado APK.

## 1. Backup: menos cópias e substituição segura

- Exportação ZIP diretamente para o ficheiro temporário; imagens, avatares e legendas são lidos por blocos, sem construir o ZIP completo em memória.
- Exportações concorrentes são serializadas. Cancelamento e falhas de escrita removem o temporário e preservam o backup anterior. A cópia de segurança anterior é criada antes da promoção do novo ficheiro; a promoção usa substituição atómica quando suportada pelo sistema de ficheiros.
- A inspeção das secções e a pré-visualização conservam apenas o JSON. Os restantes conteúdos continuam a ser percorridos e validados, incluindo limites de expansão.
- Backups cifrados são autenticados completamente num temporário privado antes de interpretar o ZIP ou iniciar o restauro. O temporário é removido no fim; resíduos antigos são limpos após 24 horas.
- Mantidos o formato `.own`, AES-GCM, parâmetros de derivação, JSON legado e regras existentes de credenciais. Um backup com palavra-passe continua a guardar os segredos previstos; um backup sem palavra-passe continua a omiti-los. Utilizadores, URLs M3U e configurações conservam a serialização existente.

Limites defensivos: JSON de 32 MiB, cada anexo de 16 MiB, conteúdo expandido total de 64 MiB, ficheiro de 80 MiB, até 4 096 entradas. Ficheiros fora destes limites são rejeitados. O JSON ainda é materializado em memória, o restauro completo ainda conserva anexos limitados em memória e o fornecedor criptográfico pode reter dados internamente. Portanto, não se promete memória constante em todas as operações.

## 2. EPG: agrupar trabalho e proteger as escritas

- Pedidos próximos de preenchimento a partir da cache são agrupados numa janela de 50 ms. Pedidos já abrangidos juntam-se à leitura em curso; os restantes aguardam a leitura seguinte. Esta janela aplica-se ao preenchimento do EPG, não à mudança de canal.
- A leitura XML decorre em IO e escreve programas em temporários privados, em vez de manter todos os programas em listas. São conservados conjuntos de identificadores e lotes limitados para inserir.
- Substituição dos programas numa transação: erro de leitura, ficheiro truncado ou cancelamento antes da conclusão reverte a substituição. Uma fonte que falhe a leitura não perde os programas anteriores.
- A leitura verifica cancelamento/interesse dos consumidores mesmo quando o XML não contém programas relevantes. Quando todos os consumidores desistem, o trabalho deixa de ter interesse e é interrompido.
- Escritas da cache e atualização de rede da mesma fonte partilham exclusão por fonte. Fontes distintas podem continuar em paralelo. A impressão digital do ficheiro é revalidada depois de esperar pela atualização, impedindo aplicar um snapshot conhecido como desatualizado.
- Temporários limitados a 128 MiB por operação, registos individuais a 2 MiB e lotes de inserção limitados por quantidade e tamanho. Resíduos antigos são limpos; temporários ativos são removidos no fim.

Sem migração de esquema. A transação não substitui testes reais com a base de dados Android, sincronização de rede e utilização do guia.

## 3. M3U: mapa de identificação construído por páginas

- Projeções de hashes existentes lidas em páginas de 400 linhas, por `id`, e colocadas diretamente no mapa necessário à sincronização.
- Removida a lista completa transitória adicional; o mapa final continua necessário e é conservado.
- Mantidas as regras de identificação, ordenação, duplicados e remoção de itens antigos. Sem alteração do esquema.
- Consulta verificada em SQLite local com 1 003 linhas, lacunas de IDs e fontes distintas. O plano utiliza o índice existente de `sourceId` e a condição sobre `rowid`, sem ordenação temporária. Esta verificação não é um benchmark na box.

## Validação

Validação final concluída com `BUILD SUCCESSFUL`:

- Compilação Kotlin `standardRelease`: aprovada.
- `lintVitalStandardRelease`, incluindo as verificações de recursos/i18n configuradas: aprovado.
- Testes JVM da app: 62, sem falhas, erros ou testes ignorados.
- Testes JVM do Core: 1 077, sem falhas, erros ou testes ignorados.
- Total: 1 139 testes; 19 testes novos neste lote.
- Compilação dos testes instrumentados Android do Core: aprovada.
- Verificação de espaços/conflitos no diff dos ficheiros principais deste lote: aprovada.

Os testes Android do contentor cifrado e da compatibilidade com o formato anterior foram compilados; não foram executados num dispositivo. Os testes JVM verificam o formato criptográfico, limites, cancelamento, preservação do ficheiro anterior, paginação, spool de EPG e coordenação concorrente.

## Testes de utilização antes de publicar

1. Exportar e restaurar backup cifrado antigo e novo, confirmando fontes Xtream, utilizadores, URLs M3U e configurações; confirmar a regra de omissão de segredos num backup sem palavra-passe.
2. Cancelar exportação e simular armazenamento insuficiente; confirmar que o backup anterior permanece utilizável.
3. Fazer vários preenchimentos de EPG durante uma atualização da mesma fonte; confirmar programas corretos e ausência de substituição por cache antiga.
4. Sincronizar uma lista grande e confirmar IDs, personalizações e ordenação existentes.
5. Medir memória, tempo de backup e sincronização num smartphone e numa box. A redução das cópias está verificada no código, mas a melhoria de CPU, RAM e tempo real ainda não foi medida.
