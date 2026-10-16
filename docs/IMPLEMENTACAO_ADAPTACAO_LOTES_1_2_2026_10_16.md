# Adaptação TV → Mobile — lotes 1 e 2

Data: 16/10/2026. Âmbito: Mobile e Core privado. Implementação dos lotes 1/2 do [plano](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/docs/PLANO_ADAPTACAO_APP_CHANNEL_MOBILE_COUNCIL_2026_10_15.md). **Implementados; compilação, testes unitários e lint aprovados.** Ensaios físicos pendentes.

Trabalho repartido por especialistas de backup/dados, perfis e gravações, com revisão cruzada e integração pelo coordenador. TV e Core externo mantidos apenas como referência. Sem APK, commit ou publicação.

## Lote 1 — integridade, configurações e privacidade

- **FTS-only:** deixa os índices B-tree disponíveis durante a importação. Preparação DDL e registo do escritor ficam protegidos contra cancelamento; uma falha de SQL faz rollback da transação. A restauração mantém a propriedade até concluir e continua a aguardar o último escritor. Não houve migração nem aumento de paralelismo.
- **Conteúdo do backup:** oito opções existentes de gravação/multiview entram nos allowlists. Defaults efetivos relevantes são obtidos de um único snapshot DataStore. Metadados opcionais distinguem valores portáteis implícitos de ausências em backups antigos; os antigos mantêm merge. Caminhos/permissões e confirmações próprias do dispositivo não são eliminados por esses metadados.
- **Validação simétrica:** a exportação valida o próprio resultado com o contrato da importação. Opacidade de fundo das legendas aceita -1 como predefinição e 0..100 como valores explícitos. Limites de margens de gravação e número de tiles são validados.
- **Identidades:** opções dependentes de lista/perfil não usam um ID numérico sem associação resolvida. Um backup sem SOURCES transporta apenas ID antigo e fingerprint SHA-256 da identidade da fonte, sem URL/utilizador/MAC/password nesse metadado; o restauro compara com fontes existentes sem criar ou alterar listas. Correspondências ambíguas são ignoradas.
- **Autenticação:** estrutura, KDF, campos malformados e truncagem identificável não são classificados genericamente como senha errada. Falha GCM apresenta “palavra-passe incorreta ou ficheiro alterado”, pois a autenticação não distingue essas duas causas. Formato cifrado e decifragem em streaming preservados; Base64 padrão compatível com os ficheiros anteriores.
- **Diagnóstico HTTP do catálogo:** origem sem caminho/query/userinfo, indicação booleana de UA personalizado e campos controlados de erro/encoding. Sem mensagens/Throwable brutos nestes logs. Os pedidos, headers, User-Agent, cancelamento e códigos HTTP conservam o comportamento anterior.
- **Perfis protegidos:** mudar/remover PIN, reduzir restrições infantis e apagar perfil protegido exigem autorização do próprio alvo. Grant de uso único, 120 s e ligado a PIN/estado/ação. O DAO confirma a proteção na escrita e impede eliminar o último perfil. Avatar altera apenas o seu campo e deteta alvo desaparecido.
- **Interface de perfis:** teclado e sheets Mobile; estado ocupado/erro, sem submissões repetidas e sem fechar antes de confirmar a escrita. PIN atual apenas em memória, sem SavedState ou logs. Os consumidores do editor no setup foram adaptados à assinatura e ao resultado da operação.

### Compatibilidade do backup

Mantidos o formato v25, as secções existentes e o transporte SAF do Lote B anterior, incluindo recordCompletion apenas após o destino final concluir. Não foi importada a UI de backup TV.

Backups antigos sem dados que identifiquem a fonte não permitem ligar com segurança opções a uma lista estrangeira só porque o ID coincide: essas opções são ignoradas quando não existe associação. Backups completos continuam a resolver pelas fontes transportadas. Os metadados novos permitem resolver também exports apenas de definições.

Os defaults portáteis não materializados e sem valor efetivo explicitamente transportado usam o default da versão de destino. Não se promete equivalência entre defaults desconhecidos de builds diferentes. “Manter configurações do dispositivo” também preserva opções de hardware implícitas.

## Lote 2 — gravações e disponibilidade

- **RAW parcial:** um arquivo que já recebeu bytes não repete um corpo completo sobre o prefixo. O guard conserva bytes tentados/confirmados da execução, além do tamanho reportado, porque alguns fornecedores SAF podem informar zero ou dados desatualizados. Bytes incertos bloqueiam retry; só bytes confirmados contam como escritos. Resposta 206 sem contrato Range validado é recusada.
- **HTTP:** abertura, playlist e chave conservam status e Retry-After tipados. A espera é monotónica, cancelável e limitada pela janela de gravação; não cria tentativas infinitas nem muda o HLS do leitor.
- **DASH:** sucesso apenas depois de copiar, concluir stop() do muxer e verificar saída MP4. Falha não apaga as tracks de origem necessárias à recuperação. Sem recodificação nem nova biblioteca.
- **Cópia SAF de finalização:** saída fechada antes de confirmar tamanho; cópia cooperativa com cancelamento. Falha/conteúdo incompleto não é tratado como promoção concluída.
- **Interrupção:** a captura conserva a marca de cancelamento antes da finalização em NonCancellable, impedindo que um arquivo interrompido com bytes seja apresentado como completo.
- **Disponibilidade:** estados de ficheiro inexistente, armazenamento desligado, permissão e leitura. Verificação read-only em IO; não cria pastas, apaga linhas ou troca destinos. A lista atualiza apenas enquanto está ativa/subscrita; alterações de progresso/título/ordem não reiniciam os probes das gravações terminadas.
- **Abertura:** nova verificação de disponibilidade e contexto do perfil antes de entregar ao leitor. Gravações repartidas mantêm a validação dos segmentos ao abrir; não há varrimento periódico de todas as partes.

## Validação e evidência

| Verificação | Resultado final |
|---|---:|
| Testes Mobile | 92, zero falhas/erros/ignorados |
| Testes Core privado | 1.154, zero falhas/erros/ignorados |
| Testes player | 448, zero falhas/erros/ignorados |
| Total | **1.694** |
| Testes unitários novos | 43: 40 Core e 3 Mobile |
| Compilação Kotlin Standard Release | Aprovada |
| lintVitalStandardRelease | Aprovado |
| Testes Android Core | Compilados; 10 casos novos, não executados |
| Inventário i18n | Aprovado: Mobile 0 baseline; Core 311 preexistentes, sem aumento |
| Recursos EN/PT alterados/novos | 7 XML analisados sem erro |
| Verificação de whitespace | Aprovada |

Execução integrada aprovada em 6 min 52 s. A confirmação após os últimos ajustes também passou: **BUILD SUCCESSFUL em 2 min 1 s**, sem o aviso Kotlin novo de safe-call desnecessária, corrigido na origem. Não se criou baseline de lint nem se suprimiu um erro para passar.

Tarefas executadas conjuntamente: :app:compileStandardReleaseKotlin, :app:testStandardDebugUnitTest, :OwnTV_Core:core:testDebugUnitTest, :OwnTV_Core:player-core:testDebugUnitTest, :OwnTV_Core:core:compileDebugAndroidTestKotlin e :app:lintVitalStandardRelease, com --no-parallel e o JDK do Android Studio.

Registo final: [owntv-mobile-lotes12-confirmed.log](C:/Users/renat/AppData/Local/Temp/owntv-mobile-lotes12-confirmed.log). Resultados contados a partir dos XML das três suites, sem somar duas execuções do mesmo teste.

Foram acrescentados testes unitários de autenticação/estrutura, snapshots portáteis, identidade de restauro, privacidade HTTP, autorização de perfis, RAW/HTTP/retry, finalização/cópia e disponibilidade ligada à visibilidade. Acrescentados testes Android com o driver Room de produção para FTS/cancelamento e escritas de perfil, mais DataStore real para roundtrip das definições.

Não há dispositivo ADB ligado. Os testes Android podem ser compilados, mas não se apresentam como executados. Os testes de identidade JVM não substituem um restauro completo entre duas instalações Android. MediaMuxer, permissões, USB/fornecedores SAF e legibilidade dos sheets exigem ensaio físico.

Snapshot anterior dos fontes/recursos e inventário: C:/Users/renat/AppData/Local/Temp/owntv-mobile-lotes12-20261016-092544. Há alterações anteriores no checkout; não foi usado reset/clean nem substituição integral de ficheiros pelo doador.

## Aceitação no dispositivo

1. Exportar/restaurar backup novo protegido e sem proteção; conferir opções por canal/lista, tempos, prioridades, modos e gravações. Ensaiar backup antigo e erro de autenticação/estrutura.
2. Importar lista nova e interromper/repetir sincronização; procurar/percorrer canais depois, sem erro de esquema.
3. Editar perfil protegido com PIN errado/correto; trocar PIN entretanto, alterar avatar e tentar apagar o último perfil. Falha não fecha o editor como sucesso.
4. Gravar e interromper RAW, HLS e DASH quando disponíveis; ficheiro parcial preservado e concluído válido reproduzível. Confirmar cancelamento de arquivo como parcial quando interrompido.
5. Repetir finalização em armazenamento interno e SAF; retirar/repor USB e revogar acesso. A gravação continua listada e pode recuperar disponibilidade.
6. Sair da lista de gravações, regressar e mudar perfil durante uma abertura; não iniciar reprodução antiga nem continuar sondagens da lista oculta.

Mantidos volume interno 100%, motores, HLS estrito, buffers, latência, Cast, poupança de dados, diferidos e os contratos dos lotes A/B Mobile anteriores. Nenhuma medição de CPU/RAM ou garantia de ausência de falhas do fornecedor é atribuída a estes testes.

Próximo lote do plano: **3 — Guia e desempenho**; não implementado neste pedido.
