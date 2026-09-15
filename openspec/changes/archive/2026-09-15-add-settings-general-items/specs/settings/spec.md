# settings Capability（delta for M2：add-settings-general-items）

> M1 已建立 modal shell + 数据底座；M2 增加 4 个设置项的具体 REQUIREMENTS + reveal/file-path 端点。

## ADDED Requirements

### Requirement: User can change appearance preference

The system SHALL allow the user to select one of three appearance preferences: `light`, `dark`, `system`.

#### Scenario: User selects dark theme
- **WHEN** the user clicks the "深色" card in the appearance row
- **THEN** the system SHALL PATCH `general.appearance.preference` with value `"dark"`
- **AND** the `<html data-theme>` attribute SHALL become `"dark"` within 100ms

#### Scenario: User selects follow-system preference
- **WHEN** the user selects "跟随系统"
- **THEN** the system SHALL PATCH with value `"system"`
- **AND** the resolved `<html data-theme>` SHALL reflect `prefers-color-scheme`

#### Scenario: System preference tracks OS theme change
- **WHEN** the user has preference `"system"`
- **AND** the OS theme changes
- **THEN** the `<html data-theme>` SHALL update without page reload

### Requirement: User can change permission mode

The system SHALL allow the user to select one of four permission modes: `plan`, `ask`, `danger-full`, `dontAsk`.

#### Scenario: User selects plan mode
- **WHEN** the user selects "plan" from the permission dropdown
- **THEN** the system SHALL PATCH `general.permission.mode` with value `"plan"`
- **AND** the local store SHALL reflect the new value immediately

#### Scenario: Permission mode persistence across reload
- **WHEN** the user has set mode to `"danger-full"`
- **AND** the user reloads the page
- **THEN** the dropdown SHALL display "danger-full" as the current value

### Requirement: User can change language preference

The system SHALL allow the user to select between Chinese and English.

#### Scenario: User selects English
- **WHEN** the user selects "English" from the language dropdown
- **THEN** the system SHALL write `"en"` to `localStorage["agent-demo:language-preference"]`

#### Scenario: Language is local-only
- **WHEN** the user changes language
- **THEN** the system SHALL NOT modify `settings.yaml`
- **AND** the system SHALL display an inline hint that full i18n will come in a future release

### Requirement: User can change Enter key behavior

The system SHALL allow the user to select one of three Enter key behaviors: `send`, `queue`, `newSession`.

#### Scenario: User selects queue mode
- **WHEN** the user selects "排队发送"
- **THEN** the system SHALL PATCH `general.enterBehavior.mode` with value `"queue"`

#### Scenario: Idle Enter always sends immediately
- **WHEN** the agent is NOT running
- **THEN** pressing Enter in the Composer SHALL send the message regardless of the Enter preference

#### Scenario: Send mode drops busy Enter
- **WHEN** the preference is `send`
- **AND** the agent IS running
- **THEN** pressing Enter SHALL show a toast "agent 还在跑"
- **AND** the message SHALL NOT be sent

#### Scenario: Queue mode queues busy Enter
- **WHEN** the preference is `queue`
- **AND** the agent IS running
- **THEN** pressing Enter SHALL append the message to an in-memory queue
- **AND** when the agent finishes, the queued messages SHALL be sent in FIFO order

#### Scenario: NewSession mode opens new session on busy Enter
- **WHEN** the preference is `newSession`
- **AND** the agent IS running
- **THEN** pressing Enter SHALL prompt the user to confirm creating a new session
- **AND** on confirmation, a new session SHALL be created
- **AND** the message SHALL be sent in the new session

### Requirement: User can reveal settings file in file manager

The system SHALL provide a "在文件管理器中显示" button that opens the OS file manager with `settings.yaml` selected.

#### Scenario: Reveal on Windows
- **WHEN** the user clicks reveal on Windows
- **THEN** the system SHALL invoke `explorer.exe /select,<absolute-path>`
- **AND** File Explorer SHALL open with the file highlighted

#### Scenario: Reveal on macOS
- **WHEN** the user clicks reveal on macOS
- **THEN** the system SHALL invoke `open -R <absolute-path>`
- **AND** Finder SHALL open with the file highlighted

#### Scenario: Reveal on Linux
- **WHEN** the user clicks reveal on Linux
- **THEN** the system SHALL invoke `xdg-open <parent-dir>`
- **AND** the file manager SHALL open the parent directory

### Requirement: User can copy settings file path

The system SHALL provide a "复制路径" dropdown item that copies the absolute path to clipboard.

#### Scenario: Copy path to clipboard
- **WHEN** the user clicks "复制路径"
- **THEN** the system SHALL write the absolute path to clipboard
- **AND** the system SHALL show a toast "已复制路径到剪贴板"

#### Scenario: Clipboard API unavailable fallback
- **WHEN** `navigator.clipboard` is unavailable
- **THEN** the system SHALL fall back to `document.execCommand('copy')`
- **AND** if that also fails, SHALL show the path in a copyable text field
