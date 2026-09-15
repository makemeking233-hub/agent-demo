# settings Capability（delta for M3：add-settings-menu-placeholders）

> M1/M2 已建好"通用"菜单的实质内容；M3 把"模型/插件/Agent 预设"三个菜单接入。

## ADDED Requirements

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
