# Ggram Pure — Официальный клиент Telegram (Полная версия)

<p align="center">
  <img src="art/ggram_pure_logo.png" alt="Ggram Pure Logo" width="160" height="160" />
</p>

<p align="center">
  <b>Ggram Pure</b> — 100% полный официальный клиент Telegram для Android, собранный на базе кодовой базы <b>DrKLO/Telegram</b> со всеми возможностями оригинального мессенджера, встроенной блокировкой всей рекламы и мгновенным переводом сообщений.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/База-Официальный_Telegram_Android-01ba53?style=for-the-badge&logo=telegram" alt="Base" />
  <img src="https://img.shields.io/badge/Версия-1.0.0_Full-01ba53?style=for-the-badge" alt="Version" />
  <img src="https://img.shields.io/badge/AdBlock-100%25_Блокировка-01ba53?style=for-the-badge" alt="AdBlock" />
  <img src="https://img.shields.io/badge/Переводчик-Встроенный-01ba53?style=for-the-badge" alt="Translator" />
  <img src="https://img.shields.io/badge/Лицензия-GPL--3.0-050505?style=for-the-badge&logo=github" alt="License" />
</p>

---

## ⚡ Все возможности полного клиента Telegram

### 💬 1. Полноценный Telegram
- Полный стек сетевого протокола **MTProto 2.0** с шифрованием.
- Реальная авторизация по SMS или сервисному коду Telegram во все существующие аккаунты.
- Голосовые и видеозвонки (VoIP) на базе WebRTC.
- Истории (Telegram Stories).
- Telegram Mini Apps и WebApps-боты.
- Анимированные векторные стикеры и эмодзи (Lottie/TGS).
- Секретные чаты с End-to-End шифрованием.
- Неограниченное облачное хранилище файлов до 4 ГБ.

### 🛡️ 2. 100% Блокировка всей рекламы (AdBlock Engine)
- Вырезка официальных спонсорских сообщений `TL_messages_sponsoredMessages` в каналах на уровне ядра `MessagesController.java`.
- Эвристическая фильтрация рекламных постов каналов (`#реклама`, `erid:`, `#ad`, инвайт-боты).
- Отключение всплывающих окон покупки Telegram Premium и звезд.

### 🌐 3. Встроенный мгновенный перевод сообщений (In-Chat Translation)
- Встроенная полоса реального времени и кнопка быстрого перевода сообщений в чатах.
- Перевод на русский и любые языки мира.

---

## 🛠️ Сборка из исходников
```bash
git clone --recursive https://github.com/grigfox43-hash/Ggram-Pure.git
cd Ggram-Pure
./gradlew :TMessagesProj_AppStandalone:assembleAfatDebug
```

---

## 📜 Лицензия
Ggram Pure распространяется под лицензией **GNU General Public License v2.0 or later (GPL-2.0-or-later)**, соответствующей официальному клиенту Telegram.
