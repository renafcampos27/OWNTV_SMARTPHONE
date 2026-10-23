# Atualizações OwnCore pendentes de implementação

Última atualização: 23/10/2026.

## Mobile 1.1.1 / Core 1.0.69 — subfase 4.1 implementada

Após autorização: prioridade de áudio nativo/FFmpeg por instância, Software explícito respeitado, uma tentativa de áudio por software por seleção/item após falha elegível, retenção de idioma/faixa e propagação da escolha por canal aos diferidos/recuo. Corrigido o rótulo Automático nas opções por canal. Atributos áudio coerentes, sem novo pedido de foco; volume interno 100%. Campos de backup, schema e dependências preservados. Compilação, lint e 1.820 testes aprovados (14 novos); aceitação física pendente. Consultar o [relatório 4.1](IMPLEMENTACAO_MOBILE_1_1_1_SUBFASE_4_1_2026_10_23.md).

As subfases 4.2–4.7 continuam pendentes. A próxima é identificar a saída efetiva de áudio; não foi acrescentada deteção de rota nem calibração por saída nesta implementação.

## Mobile 1.1.1 / Core 1.0.69 — fase 3 implementada

Após autorização separada: espera por saída visível no Live Exo, propriedade/serialização do attach mpv e detach síncrono apenas sob lock de superfícies; encerramento único da origem em erros terminais com proteção de reprodução confirmada; buffer da UI coalescido, leituras JNI expiradas rejeitadas, estatísticas opcionais e regex reutilizadas. Compilação, lint e 1.806 testes aprovados (31 novos); aceitação física pendente. Consultar o [relatório da fase 3](IMPLEMENTACAO_MOBILE_1_1_1_FASE_3_2026_10_23.md).

À conclusão da fase 3, a fase 4 ficou pendente. A subfase 4.1 foi entretanto implementada conforme o registo acima; 4.2–4.7 continuam pendentes. As fases anteriores não foram substituídas nem reabertas.

## Mobile 1.1.1 / Core 1.0.69 — fase 2 implementada

Após autorização separada, libmpv atualizado para 2026.10.4 no Core privado; keyframe/probe de arquivos adaptado por carregamento, com reset no direto/software; aprendizagem automática de software por servidor retirada dos dois motores e do arranque. Mantidas as escolhas manuais e a recuperação finita por item. Schema 52, Media3 1.11.1 e políticas locais de buffer/HLS preservados. Compilação, lint e 1.775 testes aprovados; artefacto e merge nativo verificados, reprodução física pendente. Consultar o [relatório da fase 2](IMPLEMENTACAO_MOBILE_1_1_1_FASE_2_2026_10_23.md).

À conclusão da fase 2, as fases 3–4 ficaram pendentes. A fase 3 foi entretanto implementada conforme o registo acima; a fase 4 continua pendente. Não voltar a tratar as fases concluídas como pendentes sem verificar o código atual.

## Mobile 1.1.1 / Core 1.0.69 — fase 1 implementada

Após autorização para a primeira fase, integradas a limpeza por lotes das referências de categorias/ordem manual, a fusão da ordem de categorias entre listas e a correção dos seletores na edição de fontes. Sem alteração de schema, bibliotecas ou reprodução. Compilação release, lint e 1.311 testes Mobile/Core aprovados; testes Android compilaram, com execução física pendente. Consultar o [relatório da fase 1](IMPLEMENTACAO_MOBILE_1_1_1_FASE_1_2026_10_23.md).

À conclusão da fase 1, as fases 2–4 da [análise 1.1.1](ANALISE_MOBILE_1_1_1_CORE_1_0_69_2026_10_23.md) ficaram pendentes. A fase 2 foi entretanto concluída conforme o registo acima; a gestão do player/custo de execução foi depois concluída na fase 3, mantendo-se pendentes as opções avançadas da fase 4. A fase 1 não substitui o Core privado pelo upstream nem altera a política local de HLS, buffer ou volume.

## Visual Stage — implementado em 19/10/2026

O utilizador autorizou agora a adaptação do Stage à Mobile de canais. Implementação apenas visual na app; sem importar Sports/filmes/séries e sem alterar o Core. Consultar [relatório Stage](IMPLEMENTACAO_STAGE_MOBILE_2026_10_19.md). Esta autorização substitui o adiamento do visual, não o das outras opções. As decisões de 18/10 abaixo descrevem o âmbito desse lote anterior.

## Integração de 18/10/2026 — Core 1.0.68 / Mobile 1.1.0

Após autorização do utilizador, recomendações não opcionais integradas seletivamente na Mobile e no Core privado. Consultar o [relatório de implementação](IMPLEMENTACAO_MOBILE_1_1_0_CORE_1_0_68_2026_10_18.md) e a [comparação de origem](ANALISE_MOBILE_1_1_0_CORE_1_0_68_2026_10_18.md). Inclui Guia/desempenho, fuso automático opt-in por fonte, templates M3U, contexto/renovação de diferidos, pressão de memória Android14+, retenção de faixas e motor proprietário. Migração privada51→52; não copiar o upstream49. Media3/libmpv mantêm as versões já usadas. Os lotes anteriores de backup/perfis/gravações mantêm-se. Testes físicos e benchmark continuam pendentes; o relatório distingue validação automática e aceitação real.

O subsistema upstream de timeshift local não foi transplantado, por não corresponder a um consumidor ativo nesta Mobile. Sports, política de retoma de cópia local e ClearKey inline opcional continuam por avaliar, sem autorização automática por nova publicação.

## Regra para futuras análises

Sempre que se analisar uma nova versão do OwnTV Core, consultar este ficheiro juntamente com o código da nova versão e o código atual do projeto personalizado. Comparar cada pendência, registar o resultado e atualizar o seu estado. Uma nova publicação não autoriza automaticamente a implementação das pendências.

## Implementado — lote 2: recuperação de diferidos sem imagem

**Estado:** autorizado após a análise do Core 1.0.65 e implementado seletivamente no Core privado da Mobile em 09–10/10/2026. Mantém diagnóstico correto, tentativa limitada e respeito pelo motor exclusivo. Validação física continua necessária; consultar IMPLEMENTACAO_LIBMPV_2026_10_2_CORE_1_0_65_2026_10_10.md.

**Origem:** análise do OwnTV Core 1.0.62, ramo de recuperação de arquivos/diferidos sem vídeo referido como #229, em `player-core/src/main/java/tv/own/owntv/player/OwnTVPlayer.kt`.

### Problema identificado

Quando o programa diferido foi pedido corretamente, mas não apresenta imagem, o código local já tenta recuperação por descodificação de software. Num ramo em que essa tentativa falha, pode terminar com um diagnóstico inadequado de ficheiro sem fast-start.

### Proposta a reavaliar

- Corrigir a classificação e a mensagem dessa falha específica.
- Avaliar uma única tentativa com o outro motor, apenas quando as preferências de reprodução autorizarem essa troca.
- Se o utilizador escolher exclusivamente ExoPlayer ou mpv, respeitar essa escolha e apresentar o erro correto.
- Preservar a identidade e a geração da reprodução, o cancelamento de tentativas antigas e as proteções de pausa e de áudio sem vídeo; impedir ciclos de recuperação.

Esta proposta não corrige horários do EPG, pedidos que abrem o programa errado, dias de arquivo indisponíveis ou bloqueios do fornecedor.

### Critérios para uma futura decisão

1. Verificar se a nova versão altera este ramo, corrige o diagnóstico ou oferece uma recuperação mais adequada.
2. Comparar com o código local atualizado; não copiar o Core completo nem pressupor que a pendência continua igual.
3. Explicar benefício, riscos e comportamento em modo de motor exclusivo antes de propor a implementação.
4. Se aprovada, testar diferidos com vídeo, sem vídeo e apenas áudio, troca rápida de programa e respeito pelo motor selecionado.

## Contexto dos outros lotes

- **Lotes 3, 4 e 5:** implementados seletivamente em 03/10/2026; consultar o relatório de implementação abaixo para o âmbito e os limites de validação. Não voltar a tratá-los como pendentes sem verificar o código atual.
- **Lote 1:** consta da análise original, mas não foi incluído na autorização de implementação dos lotes 3, 4 e 5. Manter separado da decisão de adiar o lote 2.
- **Lote 6:** proposta opcional na análise original; não incluída na implementação dos lotes 3, 4 e 5.

Preservar nas futuras integrações: HLS estrito e exceções autorizadas por canal, escolha de motor, áudio FFmpeg do ExoPlayer, volume da app a 100%, definições por canal, diferidos, correção horária e controlo de passagem entre canais. Verificar as versões atuais da base de dados e do backup antes de adaptar alterações upstream.

## Documentos de referência

- **11/10/2026 — Core 1.0.66/libmpv 2026.10.3:** três fases integradas seletivamente no Core privado da Mobile. Não tratar estas alterações como pendentes de código; testes físicos continuam necessários. Ver [relatório](IMPLEMENTACAO_CORE_1_0_66_LIBMPV_2026_10_3_2026_10_11.md). Reconexão DASH, retry 458 de TS e partilha com TV mantêm-se condicionais; H.264 continua por canal.

- [Análise crítica do Core 1.0.62](ANALISE_CORE_1.0.62_COUNCIL_2026-10-03.md).
- [Implementação dos lotes 3, 4 e 5](IMPLEMENTACAO_CORE_LOTES_3_4_5_2026-10-03.md).

## Histórico de decisões

| Data | Decisão |
|---|---|
| 04/10/2026 | Utilizador adiou o lote 2 para futura discussão quando existir uma nova versão do Core e pediu este registo para acompanhar as próximas análises. |
| 09/10/2026 | Reavaliado Core 1.0.65 com libmpv 2026.10.2 e council. O lote 2 continua pendente, sem implementação. Há alterações novas de codec, hardware e recuperação de arquivos que devem ser adaptadas seletivamente, preservando motor exclusivo e identidade da reprodução. Consultar ANALISE_LIBMPV_2026_10_2_CORE_1_0_65_COUNCIL_2026_10_09.md. |

| 10/10/2026 | Após autorização para implementar todas as recomendações, lote 2 integrado na Mobile: erro de diferido sem imagem, recuperação finita e fallback apenas quando permitido. TV e Core externo preservados. |

## 23/10/2026 — conclusão da adaptação Mobile 1.1.1/Core 1.0.69

Subfases 4.2 a 4.7 implementadas seletivamente no Core privado Mobile: saída efetiva/estimada, calibração por saída com backup, FPS/AFR, SDR opcional, diagnóstico de perdas e recuperação opcional limitada no mesmo motor. Não tratar como pendentes de código. Testes físicos continuam pendentes; recuperação e SDR desligados por defeito. Ver [relatório](IMPLEMENTACAO_MOBILE_1_1_1_SUBFASES_4_2_A_4_7_2026_10_23.md). Mantêm-se as outras decisões condicionais deste registo.
