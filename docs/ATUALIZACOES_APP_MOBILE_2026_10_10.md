# Atualizações da aplicação Mobile

Implementação isolada em OwnTV_Mobile-main e no Core privado em vendor/OwnTV_Core. O projeto TV e o Core externo não foram alterados.

## Utilização

Em Definições → Aplicação, junto à versão instalada. As duas opções também estão indexadas na pesquisa das definições:

- **Procurar atualizações** abre a janela de verificação manual. Se houver uma versão superior com APK compatível, permite transferir e solicitar a instalação ao Android.
- **Procurar automaticamente ao iniciar** controla a verificação após cinco segundos de abertura. Usa a preferência persistente existente, incluída no backup. Uma verificação automática por processo; a verificação manual permanece disponível.
- A procura automática apenas avisa quando encontra uma versão nova. Não instala atualizações em silêncio. A apresentação é adiada enquanto o leitor ou a configuração inicial ocupam o ecrã.

## Origem das atualizações

Repositório: https://github.com/renafcampos27/OWNTV_SMARTPHONE

A configuração anterior apontava para ahXN00/OwnTV_Mobile, e a procura automática estava desativada. A configuração atual aponta para o repositório personalizado Mobile.

Verificação em 10/10/2026: a API pública devolveu v1.0.1, sem ficheiros anexados. Para distribuir uma atualização, publicar uma release pública com versão numérica superior à instalada e anexar o APK Mobile. A assinatura deve ser a mesma da aplicação instalada, e o versionCode deve aumentar. A versão publicada deve coincidir com a versão compilada. Uma chave debug usada por omissão depende do computador que gerou o APK; usar outra chave impede uma atualização normal.

Não foi criado nem publicado um APK nesta tarefa.

## Proteções

- HTTP 404 indica ausência de release pública/acesso, em vez de afirmar que a app está atualizada.
- Uma versão igual ou anterior é reconhecida antes de exigir um APK anexado.
- Seleção dos sabores de distribuição existentes: ARM combinado ou x86_64; sem fallback entre arquiteturas incompatíveis.
- Fechar a janela cancela pedidos/transferências pendentes e invalida respostas tardias.
- Mantém validação do identificador do pacote, instalação através do Android e proteção de gravações.

## Validação

Compilação StandardRelease, testes unitários (71 na app e 1 112 no Core privado) e lintVitalStandardRelease aprovados, sem falhas. Inclui quatro testes da seleção de arquitetura do APK. A interação e instalação num smartphone físico continuam a necessitar de teste no dispositivo.
