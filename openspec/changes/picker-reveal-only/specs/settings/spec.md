# settings Capability（delta for picker-reveal-only）

## MODIFIED Requirements

### Requirement: User can create workspace via path input + reveal (no native picker)

The WorkspacePickerModal SHALL accept a path directly without invoking an OS native dialog, and SHALL provide a "reveal" button to help users locate paths.

#### Scenario: Modal opens without invoking dialog
- **WHEN** the user clicks "+" in the Sidebar
- **THEN** the WorkspacePickerModal SHALL open immediately
- **AND** no OS process SHALL be spawned

#### Scenario: User reveals current path
- **WHEN** the user clicks "在资源管理器中显示"
- **AND** a path is set in the input
- **THEN** the system SHALL call /api/settings/reveal
- **AND** the OS file manager SHALL open with the file/folder highlighted within 1 second

#### Scenario: Path change auto-fills basename as name
- **WHEN** the user types a path in the folder input
- **AND** the workspace name field is empty
- **THEN** the workspace name field SHALL auto-fill with the path's basename

#### Scenario: User types path manually and submits
- **WHEN** the user types `C:\Users\86184\projects\md-main` in folder input
- **AND** types `my-ws` in name field
- **AND** clicks 选择此目录
- **THEN** the system SHALL call onSubmit("my-ws", "C:\\Users\\86184\\projects\\md-main")
- **AND** the modal SHALL close on success

#### Scenario: Empty path disables submit
- **WHEN** the folder input is empty
- **THEN** the 选择此目录 button SHALL be disabled
