# Implementação dos lotes 3, 4 e 5 do Core

Data: 03/10/2026. Integração seletiva na variante personalizada; sem substituição integral do Core.

## Lote 3 — EPG

- Novo `ProgrammeHashTracker` com arrays de tipos primitivos e tabela de dispersão, evitando os objetos Long/Pair e o conjunto adicional por programa.
- Mantido o limite global de 100 000 entradas. Agora inclui programas existentes e novos e aplica-se também a um único canal grande.
- Nova consulta Room limitada a entradas restantes + 1, para detetar canais que excedem o orçamento sem carregar o seu histórico completo em memória.
- Quando um canal excede o limite, mantém escrita direta e não elimina programas com base numa fotografia parcial.
- Preservados os programas repetidos alterados: o último conteúdo encontrado continua a atualizar a linha correspondente.
- O código local já verificava a janela de retenção antes de escrever. Mantida a cobertura integral de histórico e a correção horária dos pedidos de diferidos.
- Não importadas a deduplicação quadrática original nem alterações de eliminação de duplicados entre fontes.

Nove testes novos cobrem limites, redimensionamento, repetição/alteração de programas, entradas existentes e novas, programas removidos e timestamps extremos.

## Lote 4 — TLS

- Adicionadas ISRG Root X2, Root YE e Root YR a partir dos certificados oficiais Let's Encrypt.
- Cada certificado foi comparado por DER com o original; os hashes estão fixados nos testes.
- O cliente HTTP usa primeiro a confiança do Android. Só tenta as raízes suplementares após rejeição por certificado.
- Mantidas validação de cadeia e verificação do nome do servidor; autenticação de cliente permanece no gestor do sistema.
- O cliente de streaming baseado nesse OkHttp herda a configuração. O TLS nativo do mpv é independente.
- Não foi desativada a validação TLS, nem introduzido trust-all.

Oito testes novos cobrem hashes e propriedades CA, assinatura, aceitação das raízes, preferência pelo sistema, falhas e confiança de cliente. Não equivalem a testes de todas as cadeias/servidores em dispositivos Android físicos.

## Lote 5 — mpv

- Dependência atualizada de `dev.jdtech.mpv:libmpv:1.0.0` para `tv.own.owntv:libmpv:2026.10.0`.
- Repositório público limitado exclusivamente ao módulo libmpv nos dois projetos. Não altera a origem do Core personalizado ou do player-core.
- Manifesto AAR declara Android mínimo 26; bibliotecas ARMv7, ARM64 e x86_64 presentes. Notas Android ELF das bibliotecas verificadas.
- Hash SHA256 do AAR obtido do Maven e usado pelo Gradle: `6d899355963ed1ceaa8d82f1ee03f3226ce5682e0745d0800eadf9b37d4df449`.
- Bibliotecas de 64 bits verificadas com segmentos PT_LOAD alinhados a 16 KiB. JNI e dependências inspecionados. Isto não substitui execução em dispositivos.
- Mantidas as bibliotecas e fontes verificadas do descodificador FFmpeg de áudio Media3/ExoPlayer.
- Adicionada lista de codecs elegíveis para hardware no mpv, incluindo MPEG-2/MPEG-4. A opção de software permanece prioritária; a inclusão não garante suporte físico do descodificador.
- Integrado callback nativo `endFile(reason,error)`. STOP explícito e créditos de encerramento são distinguidos de uma falha real; a geração da sintonia é validada no emparelhamento.
- Confirmada no dispatcher JNI da versão fixada a ordem `endFile` seguido de `event(END_FILE)` na mesma thread.
- Volume da aplicação continua a 100%. Preservados HLS estrito, áudio FFmpeg, controlo de passagem entre canais e preferências por canal.

Doze testes novos cobrem a política de codecs e a classificação de eventos. O lote 2 (#229) e o lote 6 (aprendizagem de formatos mistos) não fazem parte desta intervenção.

Referências: [libmpv publicado](https://github.com/ahXN00/OwnTV_libmpv/releases/tag/v2026.10.0), [dispatcher JNI fixado](https://github.com/ahXN00/OwnTV_libmpv/blob/v2026.10.0/libmpv/src/main/cpp/event.cpp), [certificados Let's Encrypt](https://letsencrypt.org/certificates/).

## Validação

Validação concluída: BUILD SUCCESSFUL em 4 min 8 s. Compilação Kotlin da app/Core/player-core, compilação dos testes Android e integração das bibliotecas nativas aprovadas. Os testes Android foram compilados, mas não executados num dispositivo.

Testes unitários: 1 752 aprovados — app 307, Core 1 028, player-core 417; zero falhas, erros ou testes ignorados. Incluem 29 testes novos dos três lotes. O registo desta execução não contém avisos de compilação Kotlin/Java.

Já verificados: hash do AAR efetivamente resolvido pelo Gradle; integração nativa sem conflitos de ficheiros; coexistência de libmpv/libffmpegJNI nos diretórios ARM; integridade das fontes Java e binários FFmpeg anteriores; inventário de internacionalização sem novas mensagens de interface não traduzidas.

Registo: `%TEMP%\owntv-lotes345-validation.log`.

Os testes nativos de carga dos dois motores, áudio e reprodução não foram executados em boxes. Devem incluir Xiaomi/Thomson/Mortal, AAC/MP2, hardware/software, diferidos e mudanças repetidas de canal. Não há benchmark físico para quantificar a redução de lentidão.

## Preservação e reversão

Base de dados permanece no esquema 50 e backup no formato 25. Sem migração de dados ou mudança de versão da aplicação. Nenhum APK foi gerado.

Originais dos nove ficheiros existentes alterados guardados em:
`C:\Users\renat\Downloads\OwnTV-lotes-3-4-5-backup-20261003-234822.zip`.

Novos ficheiros desta integração:

- `core/src/main/java/tv/own/owntv/core/repository/ProgrammeHashTracker.kt`;
- `core/src/test/java/tv/own/owntv/core/repository/ProgrammeHashTrackerTest.kt`;
- `core/src/main/java/tv/own/owntv/core/network/ExtraTrustAnchors.kt`;
- `core/src/main/res/raw/isrg_extra_roots.pem`;
- `core/src/test/java/tv/own/owntv/core/network/ExtraTrustAnchorsTest.kt`;
- `player-core/src/test/java/tv/own/owntv/player/MpvNativePolicyTest.kt`;
- `tools/mpv/native_manifest.json`.

Uma reversão completa deve restaurar os originais e retirar os novos ficheiros de código/testes relacionados. O relatório pode ser conservado como registo. Não usar reset global ou o Core upstream completo para reverter, pois há alterações anteriores que precisam de permanecer.
