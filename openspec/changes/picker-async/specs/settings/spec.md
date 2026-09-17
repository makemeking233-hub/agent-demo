# settings Capability（delta for picker-async）

## MODIFIED Requirements

### Requirement: User can create a new workspace via OS native folder picker (async)

The workspace picker SHALL return a task ID immediately and SHALL poll for the result, so the UI does not block on the OS dialog.

#### Scenario: User clicks pick folder, picker returns task ID immediately
- **WHEN** the user clicks "选择文件夹..."
- **THEN** the system SHALL POST /api/workspaces/pick-folder and receive a 202 with `{ task_id }` within 200ms
- **AND** the modal SHALL display "选择中..." state and begin polling

#### Scenario: User selects a folder in OS dialog
- **WHEN** the OS dialog is open
- **AND** the user selects a folder and confirms
- **THEN** the polling SHALL detect `status: "done"`
- **AND** the modal SHALL display the selected path
- **AND** the modal SHALL populate the workspace name field with the basename

#### Scenario: User cancels the OS dialog
- **WHEN** the user dismisses the OS dialog
- **THEN** the polling SHALL detect `status: "cancelled"`
- **AND** the modal SHALL remain open without showing an error

#### Scenario: User closes modal during OS dialog
- **WHEN** the user closes the workspace picker modal
- **AND** the OS dialog is still open
- **THEN** the system SHALL send DELETE /api/workspaces/pick-folder/{task_id}
- **AND** the OS process SHALL be destroyed within 1 second

#### Scenario: Reveal button provides quick alternative
- **WHEN** the user clicks "在资源管理器中显示"
- **THEN** the system SHALL call /api/settings/reveal (existing endpoint)
- **AND** the OS file manager SHALL open within 1 second

#### Scenario: Backend timeout
- **WHEN** the user does not respond within 5 minutes
- **THEN** the polling SHALL detect `status: "timeout"`
- **AND** the modal SHALL display "操作超时，请重试"
