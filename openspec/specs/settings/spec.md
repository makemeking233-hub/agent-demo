# settings Specification

## Purpose
TBD - created by archiving change add-settings-foundation. Update Purpose after archive.
## Requirements
### Requirement: Settings modal is openable from TopBar

The system SHALL display a centered modal dialog when the user clicks the settings (gear) button in the TopBar, replacing the previous alert placeholder.

#### Scenario: Click gear button opens modal
- **WHEN** the user clicks the TopBar gear button
- **THEN** the system SHALL open a modal dialog with `role="dialog"` and `aria-modal="true"`
- **AND** the modal title SHALL be "设置"

#### Scenario: Modal is closable via three paths
- **WHEN** the modal is open
- **THEN** the system SHALL close the modal when any of these occurs:
  - User presses the `Escape` key
  - User clicks the modal mask (outside the panel)
  - User clicks the close (×) button

#### Scenario: Focus returns to trigger on close
- **WHEN** the modal closes
- **THEN** the system SHALL restore keyboard focus to the TopBar gear button that opened it

### Requirement: Settings are persisted in a YAML file

The system SHALL read and write user settings from `~/.agent-demo/settings.yaml`.

#### Scenario: File does not exist on first launch
- **WHEN** the settings file does not exist
- **THEN** the system SHALL create the directory `~/.agent-demo/` if missing
- **AND** the system SHALL create the file with default values

#### Scenario: File is read with preserved unknown fields
- **WHEN** the settings file contains fields not recognized by the current schema
- **THEN** the system SHALL preserve those fields when writing back

#### Scenario: Writes are atomic
- **WHEN** the system writes the settings file
- **THEN** the system SHALL use a temporary file + atomic rename
- **AND** the system SHALL never leave a half-written file

#### Scenario: File permissions follow JSONL 0700/0600 rule
- **WHEN** the settings file or directory is created
- **THEN** the directory SHALL have mode `0700`
- **AND** the file SHALL have mode `0600`

### Requirement: Settings API supports GET and PATCH

The system SHALL expose a REST API at `/api/settings` for reading and updating settings.

#### Scenario: GET returns full settings
- **WHEN** a client calls `GET /api/settings`
- **THEN** the system SHALL return `200 OK` with body `{ version, general, revision }`

#### Scenario: PATCH updates a single field
- **WHEN** a client calls `PATCH /api/settings/general/appearance/preference` with `{"value":"dark","revision":<n>}`
- **THEN** the system SHALL validate the value
- **AND** the system SHALL atomically write to the YAML file
- **AND** the system SHALL return `200 OK` with the updated full settings (including new revision)

#### Scenario: PATCH with invalid value returns 400
- **WHEN** a client calls PATCH with a value not in the allowed enum
- **THEN** the system SHALL return `400 Bad Request` with an error body
- **AND** the system SHALL NOT modify the YAML file

#### Scenario: PATCH with stale revision returns 409
- **WHEN** a client calls PATCH with a `revision` that does not match the current revision on disk
- **THEN** the system SHALL return `409 Conflict` with the latest full settings

#### Scenario: GET nonexistent path returns 404
- **WHEN** a client calls PATCH with a path not in the schema
- **THEN** the system SHALL return `404 Not Found`

### Requirement: Settings changes propagate via SSE

The system SHALL broadcast a `settings.changed` SSE event whenever the YAML file is modified by any client or external editor.

#### Scenario: External file change triggers SSE event
- **WHEN** an external process writes to `~/.agent-demo/settings.yaml`
- **THEN** the file watcher SHALL detect the change within 1 second
- **AND** the system SHALL broadcast a `settings.changed` event to all connected SSE subscribers

#### Scenario: PATCH through API triggers SSE event
- **WHEN** a client successfully PATCHes a setting via the API
- **THEN** the system SHALL broadcast a `settings.changed` event
- **AND** the broadcasting client SHALL also receive the event (loopback consistency)

#### Scenario: SSE connection auto-reconnects
- **WHEN** the SSE connection is lost
- **THEN** the client SHALL attempt to reconnect with exponential backoff (max 30s)
- **AND** after reconnect the client SHALL call `GET /api/settings` to resync

### Requirement: Frontend store exposes a single hook

The system SHALL provide a `useSettingsStore()` React hook that exposes the current settings snapshot, a `patch(path, value)` function, and a `status` indicator.

#### Scenario: Hook returns current snapshot
- **WHEN** any component calls `useSettingsStore(s => s.snapshot)`
- **THEN** the hook SHALL return the latest snapshot or `null` if not yet loaded

#### Scenario: patch updates local state on success
- **WHEN** a component calls `patch("general.appearance.preference", "dark")`
- **AND** the server returns 200 with new snapshot
- **THEN** the hook SHALL update the local snapshot
- **AND** all subscribers SHALL re-render with the new value

#### Scenario: patch preserves old value on error
- **WHEN** a component calls `patch(...)`
- **AND** the server returns 4xx
- **THEN** the hook SHALL preserve the old snapshot value
- **AND** the hook SHALL set the `error` field with details

#### Scenario: Hook subscribes to SSE on first use
- **WHEN** the hook is first called by any component
- **THEN** the system SHALL call `GET /api/settings` to initialize
- **AND** the system SHALL subscribe to `GET /api/settings/events` SSE endpoint

### Requirement: Settings modal has 4-item left navigation

The modal SHALL display a left navigation rail with exactly 4 menu items: 通用设置, 模型, 插件, Agent 预设.

#### Scenario: Nav items render with correct labels and aria-current
- **WHEN** the modal is open
- **THEN** the system SHALL render 4 buttons with labels "通用设置", "模型", "插件", "Agent 预设"
- **AND** the active item SHALL have `aria-current="true"`

#### Scenario: Click nav item switches content area
- **WHEN** the user clicks a nav item
- **THEN** the content area SHALL render the corresponding section component

#### Scenario: M1 content area shows placeholder
- **WHEN** the user activates any nav item in M1
- **THEN** the content area SHALL render a placeholder (M2 will replace for 通用设置; M3 for the rest)

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

### Requirement: User can change default model from settings

The system SHALL display the model selector inside the "模型" menu, sharing state with the TopBar model selector.

#### Scenario: Model selector renders inside settings
- **WHEN** the user activates the "模型" nav item
- **THEN** the system SHALL render `ModelSelect` and `ReasoningEffortSelect` components
- **AND** both SHALL display the same values as the TopBar selectors

#### Scenario: Changing model in settings updates TopBar
- **WHEN** the user selects a different model in the settings panel
- **THEN** the TopBar model selector SHALL reflect the new value
- **AND** the value SHALL persist via the same mechanism as TopBar (localStorage in M3)

#### Scenario: Reasoning effort selector renders inside settings
- **WHEN** the user activates the "模型" nav item
- **THEN** the system SHALL render the reasoning effort dropdown
- **AND** the available efforts SHALL match the current model's `reasoningEfforts`

### Requirement: Plugin menu shows placeholder

The system SHALL display a placeholder when the user activates the "插件" nav item.

#### Scenario: Plugin menu shows empty state
- **WHEN** the user activates "插件"
- **THEN** the system SHALL render a `SettingsEmpty` component
- **AND** the empty state SHALL show the `Plug` icon
- **AND** the empty state SHALL contain the text "将在后续版本接入"

### Requirement: Agent presets menu shows placeholder

The system SHALL display a placeholder when the user activates the "Agent 预设" nav item.

#### Scenario: Agent presets menu shows empty state
- **WHEN** the user activates "Agent 预设"
- **THEN** the system SHALL render a `SettingsEmpty` component
- **AND** the empty state SHALL show the `User` icon
- **AND** the empty state SHALL contain the text "将在后续版本接入"

### Requirement: Settings nav supports 4-item switching

The system SHALL allow the user to switch between all 4 nav items and the active state SHALL be visually indicated.

#### Scenario: User can switch between all 4 items
- **WHEN** the user clicks each of "通用设置", "模型", "插件", "Agent 预设" in sequence
- **THEN** the content area SHALL update to show the corresponding section
- **AND** the clicked item SHALL have `aria-current="true"`
- **AND** the previously active item SHALL have `aria-current` removed

### Requirement: User can change appearance preference via TopBar popover

The TopBar theme button SHALL display the current preference icon (Sun for light, Moon for dark, Monitor for system) and SHALL open a popover containing 3 appearance cards when clicked.

#### Scenario: User clicks TopBar theme button to open popover
- **WHEN** the user clicks the theme button in TopBar
- **THEN** the system SHALL open a popover containing 3 appearance cards
- **AND** the popover SHALL be positioned right-aligned to the button
- **AND** the button icon SHALL reflect the current preference (Sun for light, Moon for dark, Monitor for system)

#### Scenario: User closes popover via outside click
- **WHEN** the popover is open
- **AND** the user clicks outside the popover
- **THEN** the system SHALL close the popover

#### Scenario: User closes popover via Escape
- **WHEN** the popover is open
- **AND** the user presses Escape
- **THEN** the system SHALL close the popover

#### Scenario: User closes popover via card selection
- **WHEN** the popover is open
- **AND** the user selects any of the 3 appearance cards
- **THEN** the system SHALL close the popover
- **AND** the preference SHALL be updated via PATCH
- **AND** focus SHALL return to the theme button

