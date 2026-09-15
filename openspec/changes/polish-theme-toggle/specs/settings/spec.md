# settings Capability（delta for polish-theme-toggle）

## MODIFIED Requirements

### Requirement: User can change appearance preference

（原 M1/M2 已定义 appearance 三卡片；本次仅改 UI 入口位置，行为不变）

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
