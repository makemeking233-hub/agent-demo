# settings Capability（delta for polish-theme-toggle）

## ADDED Requirements

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
