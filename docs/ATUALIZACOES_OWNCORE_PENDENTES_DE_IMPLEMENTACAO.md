# Atualizações OwnCore pendentes de implementação

Última atualização: 10/10/2026.

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
