# settings Capability（delta for native-folder-picker）

## ADDED Requirements

### Requirement: User can create a new workspace via OS native folder picker

The system SHALL open the OS native folder selection dialog when the user clicks "new workspace" in the Sidebar, and SHALL use the selected folder path to create the workspace.

#### Scenario: User clicks + to open picker
- **WHEN** the user clicks the "+" button in the Sidebar
- **THEN** the system SHALL open the WorkspacePickerModal
- **AND** the modal SHALL show a "选择文件夹..." button (instead of an embedded directory tree)

#### Scenario: User picks a folder
- **WHEN** the user clicks "选择文件夹..."
- **THEN** the system SHALL call POST /api/workspaces/pick-folder
- **AND** the OS native folder selection dialog SHALL open
- **AND** when the user selects a folder and confirms, the dialog SHALL close
- **AND** the modal SHALL display the selected folder path
- **AND** the workspace name field SHALL default to the folder's basename

#### Scenario: User cancels the OS dialog
- **WHEN** the OS dialog is open
- **AND** the user cancels
- **THEN** the modal SHALL remain open
- **AND** the workspace name field SHALL be empty

#### Scenario: Backend timeout
- **WHEN** the user does not respond within 5 minutes
- **THEN** the system SHALL return a timeout response
- **AND** the modal SHALL display "操作超时，请重试"

#### Scenario: User confirms workspace creation
- **WHEN** the modal shows a selected folder path
- **AND** the workspace name field is non-empty and valid
- **THEN** the system SHALL call POST /api/workspaces with the name and path
- **AND** on success, the modal SHALL close and the new workspace SHALL appear in the Sidebar

#### Scenario: Backend unavailable (e.g., no zenity)
- **WHEN** POST /api/workspaces/pick-folder returns 500
- **THEN** the modal SHALL display the error message returned by the backend
- **AND** the user SHALL be able to retry
