# Auditoria de segurança do MobileCarConnect

- **Data:** 03/11/2026
- **Versão auditada:** commit `cfba7142` (MobileCarConnect 1.0.7)
- **Escopo:**
  - código do MobileCarConnect: `app/`, `mobilecarconnect-core/`, scripts e build (~23 mil linhas);
  - os dois motores: `vendor/diauto` (Android Auto) e `vendor/diplay` (CarPlay), ~320 mil linhas;
  - todo o código nativo C/C++ próprio e os binários pré-compilados.

## Conclusão

**Nenhum código malicioso foi encontrado.** Não há envio escondido de dados, rastreadores, código
ofuscado, download e execução de código, nem backdoor. O app só se conecta à internet para checar
atualizações no GitHub, e o atualizador verifica bem o que instala.

Há **2 problemas de segurança de gravidade média e baixa** e alguns pontos de atenção. Nenhum
deles é explorável de fora do aparelho: todos exigem um app malicioso **já instalado na central**.

## Achados

> **Status (03/11/2026):** M1 e B1 foram **corrigidos** e registrados em `docs/VENDOR_CHANGES.md`.
> - **M1:** o receiver agora usa `RECEIVER_NOT_EXPORTED`.
> - **B1:** o `MapEmbedService` foi removido do manifest do MobileCarConnect (`mobilecarconnect-core/src/main/AndroidManifest.xml`).
>
> **Verificação:** o build passou, e os testes unitários de app, mobilecarconnect-core e DiAuto (1.377 testes)
> passaram, com uma única falha que não tem relação com as correções: `ConnectionLogTest` espera um
> caminho com `/` e no Windows ele usa `\`. **Não testado** na central real.

### M1 · Média: qualquer app pode mandar teclas para o Android Auto

- **Onde:** `vendor/diauto/app/src/main/java/com/andrerinas/openheadunit/aap/AapProjectionActivity.kt:1002`
- **O problema:** a tela de projeção registra um receiver **exportado e sem permissão** para a ação
  `com.andrerinas.headunitrevived.ACTION_KEYPRESS`. Cada `KeyEvent` recebido vai para o celular
  (`onKeyEvent`).
- **O que um atacante consegue:** enquanto o Android Auto estiver aberto, um app comum instalado na
  central pode mandar teclas ao celular, como mídia, atender ou encerrar chamada, navegação por setas
  e assistente de voz, sem o motorista tocar em nada.
- **Por que é assim:** quem envia são os receivers do próprio motor (`RemoteControlReceiver`,
  `CarKeyReceiver`), por broadcast implícito, sem `setPackage`. O contrato `HeadUnitIntent` também
  documenta isso como uma API para outros apps.
- **Correção sugerida:**
  1. Nos envios internos, usar `setPackage(context.packageName)`.
  2. Registrar o receiver com `RECEIVER_NOT_EXPORTED`.
  3. Se outros apps precisarem mesmo mandar teclas, proteger o canal com uma permissão `signature`
     ou com uma opção que o motorista liga.

### B1 · Baixa: o mapa do CarPlay fica aberto para qualquer app quando a opção está ligada

- **Onde:** `vendor/diplay/common/src/main/java/com/shilapi/xcertplay/MapEmbedService.kt`, exportado
  sem permissão em `vendor/diplay/common/src/main/AndroidManifest.xml:150`.
- **O problema:** com a opção "compartilhar mapa com o launcher" ligada (padrão: desligada,
  `AirPlayPersistence.loadLauncherMapSharing`), **qualquer app** pode se conectar e receber a
  imagem ao vivo do mapa do CarPlay. Essa imagem mostra onde o carro está e para onde vai. O
  serviço registra quem pediu (`sendingUid`), mas não recusa ninguém.
- **Correção sugerida:** aceitar só os pacotes que o motorista aprovar (uma lista em `attach()`) ou
  exigir uma permissão no manifest.

## Pontos de atenção (risco baixo, mas o código está como deveria)

| Ponto | Onde | Avaliação |
|---|---|---|
| TLS sem conferir certificado | `aap/AapSslContext.kt:360`, `ssl/NoCheckTrustManager.kt` (DiAuto) | Usado **só** na ligação do Android Auto com o celular, não na internet. É comum em centrais (o celular não tem um certificado que a central consiga validar). Alguém na mesma rede Wi-Fi da sessão poderia, em tese, se passar pelo celular. |
| `usesCleartextTraffic="true"` | manifest do app | Permite HTTP sem criptografia no app inteiro. Hoje nada vai para a internet sem criptografia: o atualizador exige `https://` (`UpdateHttp.kt:80`). A única exceção é o "servidor MFI remoto" do CarPlay, que aceita `http://` se o motorista digitar um endereço assim (vem vazio por padrão). |
| Portas abertas na rede durante a sessão | `AapService.kt:3321` (porta 5288), sockets AirPlay do DiPlay | Ficam abertas em todas as interfaces só enquanto há sessão sem fio. É o funcionamento normal do Android Auto e do CarPlay. |
| Eventos de "ignição ligada" e teclas de mídia de fabricantes | `SystemEventReceiver`, `RemoteControlReceiver`, `CarKeyReceiver` | Qualquer app pode imitar esses broadcasts. O máximo que isso faz é abrir a tela inicial do MobileCarConnect ou mandar play/pause. É assim de propósito, porque é como as centrais avisam. |
| Servidor HTTP de backup do DiPlay | `backup/SettingsBackupServer.kt` | Usa HTTP sem criptografia na rede local, protegido por token aleatório e código de 6 dígitos com limite de tentativas. **Não dá para chegar nele pelo MobileCarConnect:** só a `DiPlayActivity` o inicia, e ela não é exportada nem aberta pelo MobileCarConnect. |
| Root (`su`) e ADB local | `RootMode.kt`, `SystemWifi.kt`, `SUExecutor.kt`, `LocalAdb.kt` | Opcionais e desligados por padrão. Os comandos são fixos. O SSID vai entre aspas escapadas (`apConfigCommand`) e a senha vai pela entrada padrão, nunca na linha de comando. **Não achei injeção de comandos.** |
| Leitura de senhas de Wi-Fi salvas | `SystemWifi.readSavedPassword` | Só com root e só para preencher sozinho a conexão. As senhas são mascaradas nos logs e nos relatórios (`LogMasking`, `GitHubIssueReport`). |
| Acessibilidade | `WheelKeyService`, `UsbAutoConfirmService` | O primeiro lê teclas do volante e **só conta** as teclas de texto, sem guardar o que foi digitado (`WheelKeyLog`). O segundo só age nos pacotes `com.android.systemui` e `android`, para tocar em "permitir" no aviso de USB. |
| VPN | `CarPlayVpnService`, `DummyVpnService` | A do CarPlay vale só para o próprio app e para a rede local. A do Android Auto é uma VPN vazia do "modo offline": bloqueia a internet do aparelho, mas não lê nenhum pacote. |
| Carregamento de código externo | `BydCarServerTransport.kt:30` | Carrega `/system/priv-app/DiCarServer/DiCarServer.apk`, um app de sistema da BYD que não pode ser trocado sem root. Só no perfil BYD. |

## O que está bem feito

- **Atualizador** (`app/.../update/`): só instala depois de três checagens:
  1. o SHA-256 do arquivo bate com o publicado;
  2. o pacote é o mesmo e a versão é mais nova;
  3. o único assinante é a chave fixada (`UpdateVerifier.kt:9`).

  O download fica na pasta privada do app (`cacheDir/updates`) e usa só HTTPS.
- **Canal interno entre o app e o processo `:aa`:** usa broadcasts explícitos e receivers não
  exportados. O `AndroidAutoSettingsProvider` também não é exportado.
- **Chaves ADB:** ficam em `noBackupFilesDir` (privado, fora de backups).
- **Compartilhamento de arquivos:** o `MobileCarConnectFileProvider` só expõe `cache/shared/`.
- **Logs e relatórios:** mascaram endereços MAC, senhas, números de série e nomes de donos
  ("Celular do Fulano").
- **Sem rastreadores nem anúncios:** nenhum Firebase, Crashlytics, analytics ou SDK de anúncio.
- **Build:**
  - os 3 `gradle-wrapper.jar` batem com os hashes oficiais do gradle.org;
  - as dependências vêm de Google, Maven Central e, para um único pacote (`libsu`), do JitPack;
  - nenhum script baixa e executa coisas da internet;
  - o repositório não tem hooks do Git nem do Claude.
- **Código nativo próprio** (`usbhelper.c`, `ffmpeg_hevc_decoder.cpp`, `linux_i2c_jni.c`,
  `speex_echo_jni.c`, `local_hotspot_radio.c`): confere tamanhos antes de copiar memória e trata os
  erros. Não achei estouro de buffer.
- **Binários pré-compilados:**
  - FFmpeg 7.1.1, compilado com `--disable-network` e só com o decodificador HEVC;
  - libusb.

  Nenhum deles importa funções de rede nem de execução de programas.
- **Windows Defender:** não encontrou ameaças na pasta inteira.

## Como foi feito e limites

- **Ferramentas automáticas:**
  - Windows Defender;
  - hash dos wrappers comparado com a lista do gradle.org;
  - `llvm-readelf` nos `.so`;
  - análise do manifest final (merged) para listar os componentes exportados.
- **Busca por categoria de risco no código todo, lendo cada trecho encontrado:**
  - IPC e componentes exportados;
  - receivers dinâmicos;
  - servidores e clientes de rede;
  - TLS;
  - comandos de shell, root e ADB;
  - carregamento dinâmico de código;
  - armazenamento e logs;
  - permissões.
- **Lidos por inteiro:** todo o código nativo próprio, o atualizador, `RootMode`, `SystemWifi` e o
  `SystemEventReceiver`.
- **Fora do escopo:**
  - leitura linha a linha das cerca de 320 mil linhas dos motores fora desses pontos (como a lógica
    de protocolo em Kotlin, que roda em memória gerenciada);
  - o código-fonte das bibliotecas FFmpeg, libusb e speexdsp (bibliotecas conhecidas);
  - o manifest da versão release, que não foi compilada nesta auditoria;
  - testes dinâmicos de invasão.
