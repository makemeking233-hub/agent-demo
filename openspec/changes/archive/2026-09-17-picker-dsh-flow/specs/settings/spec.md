# settings Capability（delta for picker-dsh-flow）

## ADDED Requirements

### Requirement: Workspace picker uses Menu + Flow Slot (dsh alignment)

The Sidebar `+` button SHALL open a Menu listing existing workspaces plus an "Add workspace..." entry, matching dsh web's workspace add flow.

#### Scenario: User clicks + to open picker menu
- **WHEN** the user clicks the `+` button in the Sidebar header
- **THEN** a Menu SHALL open anchored below the button
- **AND** the menu SHALL list all workspaces (with check on active)
- **AND** the menu SHALL include an "Add workspace..." entry

#### Scenario: User selects existing workspace from menu
- **WHEN** the user clicks a workspace in the menu
- **THEN** the menu SHALL close
- **AND** the system SHALL invoke onPick(workspaceId)

#### Scenario: User selects Add workspace
- **WHEN** the user clicks "Add workspace..."
- **THEN** the menu SHALL close
- **AND** the OS native folder picker SHALL open (WPF OpenFolderDialog)
- **AND** when the user picks a folder and confirms, the system SHALL call createWorkspace(path)
- **AND** on success the new workspace SHALL appear in the Sidebar

#### Scenario: User cancels OS picker
- **WHEN** the user dismisses the OS picker
- **THEN** no workspace SHALL be created
- **AND** no error dialog SHALL be shown

#### Scenario: Picker error shows modal with retry
- **WHEN** the OS picker fails (timeout / platform error)
- **THEN** a Modal SHALL open with the error message
- **AND** the Modal SHALL have [Cancel] and [Choose again] buttons
- **AND** [Choose again] SHALL reopen the picker

#### Scenario: Path input fallback
- **WHEN** the picker fails repeatedly
- **THEN** the user SHALL be able to type the path directly
- **AND** the "在资源管理器中显示" button SHALL call /api/settings/reveal
