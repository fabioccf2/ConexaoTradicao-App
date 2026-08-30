# Conexão & Tradição

Aplicativo mobile (Android nativo, Kotlin) que conecta produtores rurais do Sul do Brasil
à população em geral em torno da tradição do carneamento comunitário — AA2 de
Desenvolvimento para Dispositivos Móveis (continuação da AA1).

## Status

Estrutura inicial do projeto (Parte 1 de 5): navegação entre as 5 telas do protótipo,
modelagem de dados (Room, offline-first) e camada de repositórios já criadas. Autenticação,
listagem/detalhe de eventos, chat e perfil têm a UI e o fluxo prontos; a integração real com
o Firebase depende de você criar o projeto no Firebase Console (veja abaixo).

## Configurar o Firebase (obrigatório antes de rodar de verdade)

1. Acesse https://console.firebase.google.com e crie um projeto nomeado, por exemplo,
   "Conexao e Tradicao".
2. Adicione um app Android com o pacote `com.conexaotradicao.app`.
3. Baixe o arquivo `google-services.json` gerado e substitua o arquivo placeholder em
   `app/google-services.json` por ele.
4. No console, ative: **Authentication** (métodos E-mail/senha e Google), **Firestore
   Database** (modo produção) e **Cloud Messaging**.
5. Sincronize o Gradle no Android Studio (`File > Sync Project with Gradle Files`).

## Rodar o projeto

Abra a pasta no Android Studio, deixe o Gradle sincronizar e rode no emulador ou em um
aparelho físico com Android 8.0 (API 26) ou superior.

## Arquitetura

- `data/model` — entidades de domínio (também entidades Room).
- `data/local` — Room (cache offline — RNF02): DAOs + `AppDatabase`.
- `data/remote` — integrações Firebase (Auth, Firestore, Cloud Messaging).
- `data/repository` — um repositório por área (Auth, Event, Chat, Profile), sempre
  lendo do Room e sincronizando com o Firestore em segundo plano.
- `ui/<tela>` — um pacote por tela do protótipo (Fragment + ViewModel + Adapter).

## Próximas partes

2. Autenticação real (Firebase Auth + Google Sign-In) e perfil.
3. Listagem/detalhes de evento com dados reais do Firestore + agendamento (RF07).
4. Chat em tempo real + notificações push (RF08, RF11).
5. Ajustes finos de UX/performance, relatório técnico e apresentação final.
