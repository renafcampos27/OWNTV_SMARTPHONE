# OwnTV Core 1.0.62 — comparação crítica e plano de integração

Data: 03/10/2026. Análise sem implementação, sem build e sem geração de APK.

## Âmbito e método

Comparação entre:

- Core personalizado: `C:\Users\renat\Downloads\OwnTV_Core`;
- aplicação consumidora: `C:\Users\renat\Downloads\OwnTV-main`;
- fonte extraída: `C:\Users\renat\Downloads\OwnTV_Core-core-1.0.62\OwnTV_Core-core-1.0.62`;
- base Git local: `core-1.0.57`.

As diferenças da publicação foram comparadas com a base Git e com os ficheiros atualmente modificados. Uma novidade relativamente à base pode já estar implementada na nossa variante. Não se confundiu o changelog com prova de melhoria no dispositivo.

Três agentes fizeram revisões de streaming/áudio/vídeo, EPG/dados/backup e compilação/compatibilidade/TLS. Uma segunda ronda confrontou os resultados e os riscos. O conselho recomenda integração seletiva.

Fontes oficiais: [Core 1.0.62](https://github.com/ahXN00/OwnTV_Core/releases/tag/core-1.0.62), [libmpv 2026.10.0](https://github.com/ahXN00/OwnTV_libmpv/releases/tag/v2026.10.0). As evidências detalhadas abaixo resultam da inspeção das fontes locais.

## Decisão principal

**Não substituir o Core inteiro nem alterar simplesmente o número da dependência.** O nosso esquema Room é 50 e o backup é 25; o original usa esquema 47 e backup 24. A migração 46→47 tem significados diferentes nos dois projetos.

- Original `OwnTVDatabase.kt:1233`: acrescenta detalhes XMLTV e lembretes.
- Nosso `OwnTVDatabase.kt:1347`: acrescenta reservas de buffer por fonte.
- Nosso esquema 48–50: acrescenta duração capturada, recuperação e intervalos em falta, erros de gravação e pausa de arquivo.

Copiar a base original provocaria tentativa de downgrade ou incompatibilidade de esquema. Se uma funcionalidade nova exigir tabelas/colunas, criar uma migração própria a partir da versão atual, nunca reutilizar uma migração upstream com a mesma numeração e outro conteúdo.

## Mudanças que fazem sentido

| Mudança | Situação no nosso código | Decisão |
|---|---|---|
| Categorias próprias continuam a mostrar os canais quando se oculta a categoria do fornecedor | Ainda falta distinguir o contexto da lista | Integrar. Ocultar explicitamente o canal continua a ter prioridade. |
| Proteção contra colisões de IDs ao restaurar backups | Alguns caminhos aceitam o ID antigo quando não há remapeamento | Integrar com testes de restauro parcial e de apenas configurações. |
| Diferido sem imagem deixa de receber diagnóstico incorreto de ficheiro sem fast-start | Existe um ramo restante depois da tentativa de recuperação por software | Integrar o ramo específico, respeitando o motor selecionado. |
| Sincronização EPG com estruturas primitivas | Ainda usamos mapas de objetos e conjuntos por canal | Integrar isoladamente e medir memória, alocações e tempo. |
| Filtrar programas fora da retenção antes de escrever | O original evita trabalho que depois é eliminado | Integrar preservando o histórico máximo dos canais diferidos. |
| Confiança TLS para certificados Let's Encrypt recentes | ExtraTrustAnchors está ausente no código atual | Integrar de forma controlada, sem desligar validação TLS. |
| libmpv 2026.10.0 | Ainda usamos dev.jdtech.mpv:libmpv:1.0.0 | Avaliar em lote separado com testes físicos e possibilidade de reverter. |
| Permitir aceleração mpv de MPEG-2/MPEG-4 quando suportada | A lista explícita original não está aplicada | Avaliar com o novo binário; não forçar hardware em modo software. |
| Corrigir a preferência aprendida HLS em servidores com canais TS | Novos mecanismos upstream não estão presentes | Só para exceções que permitem TS. Nunca contrariar Apenas HLS. |

### 1. Categorias próprias e visibilidade

Original `core/live/LiveQueries.kt:93–100`: `hiddenCategoryApplies` distingue uma lista do fornecedor de uma categoria construída pelo utilizador. Nosso `LiveQueries.kt:88–90` rejeita sempre a categoria do fornecedor oculta.

Implementação mínima: adaptar a regra partilhada e os filtros/validações equivalentes em `LiveViewModel`. Não basta mudar o Core: a app repete essa decisão na seleção e no zapping. `CustomCategoryDao.contextsOf` pode ajudar a localizar a categoria própria do canal de arranque.

Aceitação: ocultar a categoria do fornecedor mantém o canal na categoria própria; ocultar o canal retira-o; setas, canal de arranque e retorno do fullscreen mantêm o comportamento correto. Não reintroduzir favoritos/histórico na interface.

### 2. Restauro de backups

Original `BackupManager.kt:1768` acrescenta `remapProfileKeys`; `:1899` protege as chaves de motor quando a fonte não foi restaurada. Nosso código ainda contém `profileIdMap[filePid] ?: filePid` em vários caminhos.

Risco identificado por inspeção: um ID numérico do ficheiro pode coincidir com outro perfil/fonte local e atribuir-lhe configurações indevidas. Não foi reproduzido um restauro real nesta análise.

Implementação: rejeitar associações sem destino válido quando houver remapeamento, mantendo a semântica deliberada de restauro apenas de configurações. Preservar backup 25 e todos os ajustes locais: prioridade de versões, tempo de mudança automática, barra lateral, HLS, áudio por software e correção horária dos diferidos.

### 3. Recuperação de diferidos sem imagem — #229

Original `player/OwnTVPlayer.kt:3083–3094`: depois de falhar a recuperação por software, pode tentar o outro motor no ramo de arquivo sem imagem. Nosso ramo próximo de `:3061` ainda termina com uma classificação de fast-start inadequada para esse caso.

Implementação: transportar apenas a decisão específica. Reutilizar as proteções existentes de identidade da reprodução, tentativa única, áudio sem vídeo, pausa e autorização de troca de motor. Se o utilizador escolheu exclusivamente um motor, não o trocar; apresentar o erro correto.

Não assumir que todos os arquivos são MPEG-TS. Não confundir esta recuperação com a correção horária do EPG: #229 não corrige um pedido que abre o programa errado.

### 4. EPG com menos pressão sobre a box

Original `EpgRepository.kt:909–968`: timestamps e IDs em `LongArray`, hashes em `IntArray`, marcas em `BooleanArray`. Nosso `:795–796` usa objetos em mapas/conjuntos.

Transportar primeiro a estrutura, mantendo inicialmente o limite atual. O limite original sobe de 100 mil para 400 mil programas; não aumentar sem medir o pico de memória. As estimativas de bytes dos comentários não incluem obrigatoriamente todas as alocações temporárias.

Original `:264`, `:387`, `:419`: aplica retenção antes de inserir, evitando escrita seguida de remoção. Adaptar à nossa cobertura de diferidos e às associações de guia, incluindo canais com duração de arquivo desconhecida. Preservar pelo menos o caso relatado de programas com cinco dias.

Original `:534–536`: elimina a remoção repetida de duplicados entre fontes após cada sincronização. Pode reduzir escritas, mas aumentar os programas duplicados guardados. Medir tamanho da base, leituras e consistência quando uma fonte é removida.

**Não importar `FeedDedupe` sem revisão:** `epg/FeedDedupe.kt:22` usa `slots.any` sobre todos os programas aceites de um canal. O custo pode crescer quadraticamente. Preferir candidatos indexados por título/intervalo e validar guias grandes.

Aceitação: sincronizações sem alterações não reescrevem o guia desnecessariamente; programas alterados atualizam; removidos desaparecem; diferidos e horas permanecem corretos; comando/lista não pioram durante a sincronização. Medir tempo, alocações, pico de memória e volume de escritas.

### 5. TLS nas boxes antigas

Original `network/ExtraTrustAnchors.kt:22–54` e `di/DataModule.kt:43–61`: primeiro usa a confiança do Android; só depois tenta raízes adicionais conhecidas. Manter a verificação do nome do servidor.

Integrar classe, certificados e ligação ao cliente HTTP. Confirmar origem e impressão digital dos certificados. Testar cadeias válidas e rejeição de certificado expirado, nome errado e certificado desconhecido. Não usar trust-all.

Âmbito: clientes HTTP que herdem essa configuração. Não resolve HTTP 403, sessões do fornecedor ou automaticamente o TLS nativo do mpv. Sem erro TLS, não esperar ganho na fluidez.

### 6. Novo mpv e classificação de eventos

Original catálogo `:48`, `:133`: `tv.own.owntv:libmpv:2026.10.0`; repositório Maven público. A publicação declara ARMv7, ARM64 e x86_64, FFmpeg n9.0.2 e mpv 0.41.0-1092.

Avaliar o AAR e os binários: APIs JNI, bibliotecas duplicadas, dependências nativas, licença, requisitos Android e alinhamento. Preservar o nosso descodificador FFmpeg de áudio para ExoPlayer; esta atualização não o substitui.

O callback `endFile(reason,error)` do novo binário permite classificar melhor STOP e erro real. Integrar junto da dependência compatível; não copiar apenas o código para a biblioteca antiga.

Não transportar `tls-verify=no` global do caminho mpv original como solução de compatibilidade: desliga a validação de certificados. A solução de raízes adicionais no cliente HTTP é diferente e conserva as verificações de confiança.

O retry original para fornecedores mistos valida apenas a URL atual. Na nossa implementação deve validar também a geração/token da sintonia: A→B→A pode ter a mesma URL e ainda receber um callback antigo. A classificação de formato nunca deve transformar um HTTP 403 em tentativa TS.

Testes: Android 8/9 ARMv7 e 13/14 ARM64, Xiaomi/Thomson/Mortal; áudio AAC/MP2/AC3; hardware/software; HLS; recuo/diferidos; 100 mudanças A/B/C; cancelamento de tentativas antigas; volume da aplicação a 100%. Ganhos só podem ser afirmados depois de comparar.

## O que já existe ou não compensa transportar

- Media3/ExoPlayer continua em 1.11.1 nos dois projetos: esta publicação não é uma atualização do ExoPlayer.
- Kotlin, Room e OkHttp têm as mesmas versões relevantes. O AGP original é 9.4.0; o nosso é 9.4.1. Não fazer downgrade.
- Android mínimo continua API 26; o novo binário nativo ainda exige validação em boxes antigas.
- Já existem controlador central de sintonia, timeshift local, várias políticas de recuperação, ajustes por canal e remoção de BOM no M3U.
- Proteção contra armazenamento indisponível já existe em `MediaStorageBudget.acquire` e `MediaTarget.ensureWritable`; não remover limites de disco ao copiar DownloadEngine.
- Não copiar ExoRenderers completo: perderíamos políticas de fila/descodificação e o áudio FFmpeg personalizado.
- Não substituir o cancelamento HTTP local pela implementação original sem prova de equivalência; preservar o cancelamento de chamadas bloqueantes.
- Não transportar curvas de volume, volume superior a 100%, favoritos/histórico/canal anterior ou elementos de filmes/séries contrários às preferências do utilizador.
- Novos ícones, fundos e interface não são uma otimização de streaming. O Core fornece recursos e configurações, não implementa sozinho a nova interface.
- Detalhes XMLTV e lembretes são opcionais e exigem migração própria. Não aumentam os dias de arquivo fornecidos pelo servidor.
- Retomar timeshift guardado pode ser útil futuramente, mas não deve substituir o canal de arranque escolhido.

## Lotes aprovados pelo conselho para futura implementação

1. **Correções pequenas:** categorias próprias e proteção do remapeamento de backups. Sem novas tabelas.
2. **Diferidos:** ramo #229, erro correto e recuperação limitada às preferências de motor.
3. **Desempenho EPG:** estrutura de memória, retenção antes de escrita e deduplicação revista, com medições e testes de histórico.
4. **Compatibilidade TLS:** antecipar se houver erros de certificados; manter isolado da reprodução.
5. **mpv:** atualização nativa isolada, callbacks e codecs, com testes nas boxes e reversão preparada.
6. **Opcional:** aprendizagem HLS/TS apenas nos canais autorizados, detalhes XMLTV e lembretes conforme necessidade.

Prioridade para lentidão: lote 3. Prioridade para diferidos sem imagem: lote 2. Não há evidência suficiente para prometer que a atualização inteira elimina bloqueios de zapping ou falhas do fornecedor.

## Limites da análise

As diferenças e incompatibilidades indicadas foram confirmadas por inspeção de código. Não foram realizados benchmarks, builds da versão original, restauros reais, testes TLS em box ou testes de reprodução. Nenhuma alteração de funcionamento foi aplicada nesta análise.
