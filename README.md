# Conexão & Tradição

Aplicativo mobile (Android nativo, Kotlin) que conecta produtores rurais do Sul do Brasil
(Paraná, Santa Catarina e Rio Grande do Sul) à população em geral em torno da tradição do
carneamento comunitário de gado e porco. Produtores anunciam eventos de carneamento — com
data, local, tipo de animal, cortes disponíveis e preço por kg — e qualquer pessoa
interessada encontra esses eventos na sua região, reserva os cortes desejados e participa da
experiência.

Trabalho da disciplina de Desenvolvimento para Dispositivos Móveis — UNOESC (AA2,
continuação do protótipo projetado na AA1).

## Status

**Projeto concluído.** Todos os requisitos funcionais (RF01–RF11) e não funcionais definidos
na AA1 foram implementados e testados de ponta a ponta, com integração real ao Firebase
(Authentication + Cloud Firestore) e arquitetura offline-first (Room como cache local).

- Cadastro/login por e-mail e senha ou Google (RF01).
- Perfil com edição de nome, histórico de participações e avaliação em estrelas (RF02, RF10).
- Listagem e busca de eventos de carneamento por cidade/produto (RF03, RF04).
- Cadastro de evento pelo produtor, com captura de localização via GPS (RF05, RF09).
- Detalhes do evento, seleção de cortes e agendamento de participação (RF06, RF07).
- Chat em tempo real entre comprador e produtor (RF08).
- Notificações locais de novas mensagens no chat (RF11).
- Finalização de evento pelo produtor, com fotos da carneação e avaliação em estrelas nos
  dois sentidos (produtor ↔ comprador) (RF10).
- Exclusão de evento pelo produtor.

Testado de ponta a ponta em cenários reais, com dois emuladores rodando simultaneamente,
cada um autenticado com uma conta Firebase distinta (produtor e comprador).

## Configurar o Firebase

O projeto já vem integrado a um projeto Firebase real ("Conexao e Tradicao"). Para rodar com
sua própria instância do Firebase:

1. Acesse https://console.firebase.google.com e crie um projeto.
2. Adicione um app Android com o pacote `com.conexaotradicao.app`.
3. Baixe o arquivo `google-services.json` gerado e substitua o arquivo em
   `app/google-services.json` por ele.
4. No console, ative: **Authentication** (métodos E-mail/senha e Google) e **Firestore
   Database**.
5. Em Firestore Database → Regras, publique uma regra que libere leitura/escrita para
   usuários autenticados (necessário porque a avaliação pós-evento grava dados de uma conta
   no perfil de outra):
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /{document=**} {
         allow read, write: if request.auth != null;
       }
     }
   }
   ```
6. Sincronize o Gradle no Android Studio (`File > Sync Project with Gradle Files`).

> O plano gratuito (Spark) do Firebase não permite Cloud Functions com saída de rede, então
> as notificações (RF11) não usam Cloud Messaging/push real — o próprio app escuta o
> Firestore em tempo real e dispara a notificação localmente. Pelo mesmo motivo, as fotos do
> evento finalizado (RF10) são salvas comprimidas em Base64 direto no Firestore, em vez de
> no Firebase Storage (que hoje exige upgrade para o plano pago Blaze).

## Rodar o projeto

Abra a pasta no Android Studio, deixe o Gradle sincronizar e rode no emulador ou em um
aparelho físico com Android 8.0 (API 26) ou superior. Pra testar chat/notificações entre duas
contas, rode em dois emuladores/aparelhos simultâneos, cada um logado com uma conta
diferente.

## Arquitetura

Padrão MVVM (Model-View-ViewModel) com uma camada de Repository entre a UI e as fontes de
dados (Room + Firestore):

- `data/model` — entidades de domínio (também entidades Room): `User`, `Event`, `Cut`,
  `Participation`, `Rating`, `ChatMessage`.
- `data/local` — Room (cache offline — RNF02): DAOs + `AppDatabase`.
- `data/repository` — um repositório por área (`AuthRepository`, `EventRepository`,
  `ChatRepository`, `ProfileRepository`, `ChatNotifier`), sempre lendo do Room como fonte de
  verdade da UI e sincronizando com o Firestore em segundo plano (best-effort).
- `ui/<tela>` — um pacote por tela (Fragment + ViewModel + Adapter): login/cadastro, home,
  criação de evento, detalhe do evento, chat, perfil.
- `util` — utilitários (constantes, compressão/conversão de imagem, notificações).

**Estratégia offline-first (RNF02):** todas as telas de listagem leem primeiro do Room, que
funciona como fonte de verdade para a interface — o app nunca fica "em branco" por falta de
conexão. Escritas gravam simultaneamente no Room e no Firestore, com o envio ao Firestore
tratado como best-effort; uma rotina de sincronização (a cada abertura do app) busca do
Firestore o que ainda não está refletido localmente assim que a conexão volta.

## Entregas da AA2

- Código-fonte: este repositório.
- Relatório técnico (`.docx`): arquitetura, modelagem de dados, funcionalidades
  implementadas, desafios técnicos enfrentados e testes realizados.
- Apresentação final (`.pptx`).

## Licença

Trabalho acadêmico — UNOESC, Desenvolvimento para Dispositivos Móveis, 2026.
