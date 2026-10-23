# Mobile 1.1.1 — fase 1: listas, categorias e edição de fontes

Data: 23/10/2026. Autorização: «implementa por fases as tuas recomendações; avança com a primeira fase».

## Âmbito implementado

1. **Limpeza de referências após sincronizar:** os dois DAOs de ordem manual e membros de categorias recebem grupos de IDs, em vez de uma eliminação por registo. O agrupamento separa operação/perfil/tipo, elimina IDs repetidos e limita cada grupo a 900 IDs. Parsing e agrupamento são feitos antes de obter a transação de escrita; as eliminações continuam atómicas. Mantêm-se as condições de conteúdo inexistente, IDs capturados no snapshot e sincronização concluída. Snapshots vazios/antigos sem IDs e sincronizações incompletas conservam as proteções anteriores.
2. **Ordenação de categorias:** mover as categorias visíveis integra a ordem na configuração existente, preservando as restantes listas/categorias filtradas. Chaves duplicadas são removidas. A gravação usa a transformação atómica do DataStore e o mesmo formato JSON/backup. Categorias personalizadas partilhadas continuam a participar na ordem global; não foi criado um ranking independente por fonte.
3. **Edição de fontes:** `EditPlaylistDialog` contém agora o seu próprio `MobileSheetHost`, para os seletores do formulário aparecerem sobre o diálogo. Campos, credenciais, callbacks de guardar/cancelar e teclado mantêm o comportamento anterior.

Origem: deltas dirigidos do Core 1.0.69/Mobile 1.1.1, adaptados ao projeto privado. A estratégia de fusão conserva as outras listas; foram acrescentadas deduplicação e validação do limite dos grupos. Não foram substituídos ficheiros completos do upstream.

## Preservação

- Projeto: `C:\Users\renat\Downloads\OwnTV_Mobile-main\OwnTV_Mobile-main`.
- Snapshot anterior: `C:\Users\renat\AppData\Local\Temp\owntv-mobile-before-phase1-111-20261023`; `inventory.json` com hashes. As cópias dos seis ficheiros preexistentes editados foram verificadas por SHA256.
- Core privado `vendor/OwnTV_Core`; TV e Core externo não foram editados.
- Sem migração/schema novo, sem alterações nas versões de bibliotecas e sem alteração de reprodução. Schema 52, configuração do composite build, catálogo do Core e ficheiros principais dos reprodutores comparados com os hashes anteriores: iguais.
- HLS estrito/exceções, buffer, motor, zapping, áudio a 100%, Stage e definições por canal preservados.
- Sem APK, assinatura, instalação, commit ou publicação.

## Testes acrescentados

- `SnapshotPurgeBatchesTest`: 4 casos sobre agrupamento, limites, duplicados e isolamento por operação/perfil/tipo.
- `SnapshotPurgeSqlTest`: 3 casos executam as anotações SQL reais dos DAOs em SQLite JDBC, com as tabelas/índices de destino do schema 52. Cobrem conteúdo válido em duas fontes, outro perfil/tipo, órfãos fora do snapshot, o mesmo item em duas pastas, mais de 999 IDs e snapshot vazio.
- `CategoryOrderMergeTest`: 5 casos sobre alternância de listas, categoria partilhada, categorias ocultas, duplicados e persistência de ordem/ocultação/prioridade de versões no JSON usado pelo backup.
- `UserDataResolverRelinkTest`: 2 casos Android de integração sobre relink de ordem/membros com IDs novos, âmbito do snapshot e proteção de sincronização incompleta. Exigem dispositivo para executar.

## Validação

**BUILD SUCCESSFUL**, 5 min 35 s na execução final. Compilação Kotlin release/debug da Mobile e do Core privado aprovada; os DAOs modificados passaram o processamento Room/KSP. `lintVitalStandardRelease`: `No issues found.` Inventários i18n e `git diff --check` aprovados.

- Mobile: **119 testes**.
- Core privado: **1.192 testes**, incluindo os **12 novos** desta fase.
- Total desta execução: **1.311 testes**, zero falhas, erros ou ignorados.
- Os dois novos testes Android de integração compilaram em `compileDebugAndroidTestKotlin`, mas não foram executados. Nenhum dispositivo estava ligado.
- A suíte player-core não foi repetida: não houve alteração de código de reprodução nesta fase. A compilação dos consumidores do Core foi verificada.
- Verificação de âmbito em 1.335 ficheiros de código preexistentes encontrou alterações apenas nos seis ficheiros previstos; auxiliares/testes novos são adicionais.

Evidência: [MOBILE_111_FASE1_20261023.json](validacao/MOBILE_111_FASE1_20261023.json). Log final: `%TEMP%/owntv-mobile-111-phase1-validation-20261023.log`.

Os inventários i18n foram atualizados apenas para os identificadores JSON e SQL técnicos modificados; a entrada SQL antiga da baseline foi removida. Não foram acrescentados textos visíveis nem aumentada a dívida de tradução.

## Aceitação no dispositivo

Não havia dispositivo ADB ligado. Falta verificar:

1. Editar uma lista e abrir os seletores de atualização/sincronização; escolher, fechar, voltar e guardar.
2. Reordenar categorias da lista A, depois B e regressar a A, incluindo uma categoria personalizada partilhada.
3. Atualizar uma lista com canais em categorias personalizadas e confirmar a conservação de membros/ordem.
4. Executar os testes Android de integração e observar a duração da sincronização numa lista real grande.

Não existe benchmark físico para atribuir uma percentagem de aceleração. A mudança reduz o número de DELETEs/varrimentos repetidos das duas tabelas; não elimina todos os custos de uma sincronização.

## Próximo lote

Fase 2: libmpv 2026.10.4, abertura de diferidos em hardware com opção por arquivo e revisão da penalização automática persistida por servidor. Não incluída nesta fase.

Plano completo: [análise 1.1.1](ANALISE_MOBILE_1_1_1_CORE_1_0_69_2026_10_23.md).
