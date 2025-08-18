# SeeMegaMeteor - Плагин для Minecraft серверов
# Возможны баги тк тесты плагина ещё не проводились! (Ведуться ТЕСТЫ 1.16.5 PurPur)

version 1.2

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.16.5%2B-brightgreen)
![License](https://img.shields.io/badge/License-MIT-blue)

Плагин для создания эпичного ивента с падением метеора на вашем сервере Minecraft. Включает полный цикл событий от предупреждения до падения метеора с системой лута и визуальными эффектами.

## 📌 Основные возможности

- 🌠 Реалистичное падение метеора с визуальными эффектами
- 💎 Система генерации лута с настраиваемыми шансами
- ⏳ Автоматический запуск по расписанию или вручную
- 📊 Поддержка нескольких систем хранения данных (SQLite, JSON, YAML)
- 🎨 Настраиваемые голограммы и сообщения
- 🔧 Полная интеграция с WorldEdit для работы со схемами

## 📦 Установка

1. Скачайте последнюю версию плагина из [Releases](https://github.com/your-repo/SeeMegaMeteor/releases)
2. Поместите файл `SeeMegaMeteor.jar` в папку `plugins/` вашего сервера
3. Перезапустите сервер
4. Настройте плагин через файлы конфигурации

## ⚙️ Конфигурация

Основные файлы конфигурации:
- `config.yml` - основные настройки ивента
- `loot.yml`/`data.db` - настройки лута (формат зависит от выбранного хранилища)

Пример минимальной конфигурации:
```yaml
storage:
  type: SQLite
  backup:
    enabled: true
    interval_minutes: 60

meteor_stages:
  materials:
    initial: LODESTONE
    falling: MAGMA_BLOCK
    final: BEACON
```

## 📜 Команды

| Команда | Описание | Права |
|---------|----------|-------|
| `/megameteor` | Показать время до ивента | `seemegameteor.megameteor` |
| `/seemegameteor start` | Запустить ивент вручную | `seemegameteor.admin` |
| `/seemegameteor edit loot` | Открыть редактор лута | `seemegameteor.admin` |
| `/seemegameteor reload` | Перезагрузить конфигурацию | `seemegameteor.admin` |

## 📚 API для разработчиков

Плагин предоставляет API для интеграции с другими плагитами:

```java
SeeMegaMeteor plugin = SeeMegaMeteor.get();
LootManager lootManager = plugin.loot();
MegaMeteorEventManager eventManager = plugin.events();
```

## 🌍 Поддерживаемые версии

- Minecraft: 1.16.5+
- Java: 8+
- Зависимости: WorldEdit, WorldGuard, ProtocolLib (опционально)

## 📄 Лицензия

Этот проект лицензирован под MIT License - смотрите файл [LICENSE](LICENSE) для деталей.
