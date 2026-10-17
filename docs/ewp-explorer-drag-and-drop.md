# EWP Explorer Drag-and-Drop Specification

This document describes the design, user interactions, technical architecture, and edge-case handling for drag-and-drop reorganization within the EWP Explorer panel.

---

## 1. Goal & Principles

The EWP Explorer panel allows developers to view and manage the hierarchical group and file structure defined in IAR EWARM project files (`.ewp`).

### Key Principles

1. **Logical Reorganization vs. Filesystem Moves**:
   - Drag-and-drop within the EWP Explorer reorganizes the **logical XML hierarchy** (`<group>` and `<file>` elements) of the `.ewp` file.
   - It **does not** move or rename files on the physical filesystem.
   - Relative paths encoded with `$PROJ_DIR$` remain intact because the physical file paths and the `.ewp` location do not change.
2. **Deterministic Canonical Sorting**:
   - In EWP files, manual arbitrary insertion ordering within a group is not permitted: groups are sorted alphabetically first, followed by files sorted alphabetically by filename.
   - Any move operation automatically re-runs canonical sorting (`EwpDomSortFixer.sortAndFormat`) across the affected hierarchy.
3. **Group Preservation**:
   - Unlike external file deletion or filesystem sync (`EwpSyncFilesService`), moving the last file out of a group in the explorer does **not** prune or delete the source group. Empty groups are valid project structure and are preserved.

---

## 2. Supported User Interactions

| Source Drag Selection | Target Node in Tree | Outcome |
|-----------------------|---------------------|---------|
| **File(s)** | **Group Node** | Moves selected file(s) into the target group. |
| **File(s)** | **File Node** | Resolves target to the file's parent group; moves dragged file(s) into that group. |
| **File(s)** | **Root Node** | **Rejected** (files cannot exist at root level in EWP). |
| **Group(s)** | **Group Node** | Moves selected group(s) as subgroups into the target group. |
| **Group(s)** | **File Node** | Resolves target to the file's parent group; moves dragged group(s) into that group. |
| **Group(s)** | **Root Node** | Moves selected group(s) to top-level groups directly under `<project>`. |
| **Project Root Node** | *Any* | **Cannot be dragged** (root represents the project file itself). |
| **Multi-selection** | **Group Node** | Moves all non-redundant selected files and groups into target. |

### Post-Drop UI Behavior

After a successful drop:
1. The `.ewp` file document is modified in a `WriteCommandAction` and saved to disk.
2. The index cache (`EwpIndexService`) for the `.ewp` file is invalidated.
3. The tree is reloaded on the Event Dispatch Thread (EDT).
4. All nodes are expanded by default, and the target group (or the moved group) is selected and scrolled into view.

---

## 3. Edge-Case Decisions

### 3.1 Dropping onto a File Node

- **Decision**: Dropping onto a `FileNode` resolves to that file's parent `GroupNode`.
- **Rationale**: In trees with large numbers of expanded files, targeting the group header row can be inconvenient or off-screen. Standard IDEs (including IntelliJ and CLion) resolve dropping on a file to its containing directory. This matches the behavior of `resolveNewGroupParentPath()` in `EwpExplorerPanel`.

### 3.2 Moving to the Root Node

- **Groups**: **Allowed**. If a subgroup `services` (inside `src`) is dragged onto the project root node, it becomes a top-level group directly under `<project>`.
- **Files**: **Disallowed**. The EWP XML schema and `EwpParser` only recognize files inside `<group>` tags. `canImport` displays the "not allowed" cursor if any dragged item is a file and the target is `Root`.

### 3.3 Self and Descendant Cycles

- **Self Drop**: A group cannot be dropped into itself (`A` into `A`).
- **Descendant Drop**: A group cannot be dropped into any of its own subgroups (`A` into `A/B/C`).
- **Validation**: Enforced via `EwpFileWriter.isDescendantOrSelf(sourceGroupPath, targetGroupPath)`. If violated, `canImport` returns `false`.

### 3.4 Same-Parent / No-Op Drops

- Dropping a file into its current parent group is rejected.
- Dropping a group into its current parent group is rejected.
- Dropping a top-level group onto the Root node is rejected (it is already at root).
- If the drop would produce no change, `canImport` returns `false`.

### 3.5 Name Collisions & Duplicates

- **Duplicate Group Names**: EWP groups under the same parent must have distinct names (case-insensitive). If the target group (or root) already contains a child group with the dragged group's name, the drop is rejected.
- **Duplicate Files**: A group cannot contain the same file multiple times. If the target group already contains a file with matching path (`rawPath` or normalized resolved path), the drop is rejected.
- **Collisions Within the Dragged Batch**: If a multi-selection contains duplicate group names (e.g. `src/tools` and `libs/tools`) or duplicate files, the batch drop is rejected to prevent collisions at the target.

### 3.6 Ancestor-Descendant Multi-Selection Filtering

- When multiple items are selected in the tree (e.g., both group `src` and file `src/main.cpp`), `filterTopLevelPaths` keeps only `src`.
- Descendant nodes whose ancestor is already part of the drag selection are pruned from the transfer payload because moving the ancestor group inherently moves all of its contents.

### 3.7 XML Structure & Suffix Tag Preservation

- In EWP XML, `<projectSettings>` appears at the end of `<project>` after all `<group>` elements.
- When adding or moving a group to the project root, the group must not be appended after `<projectSettings>`.
- `addContentToContainer` ensures that new or moved elements are inserted before any trailing suffix elements (`<projectSettings>`), allowing `EwpDomSortFixer` to properly classify and sort them within the reorderable block.

### 3.8 Cross-Project Isolation

- `EwpDragData` records the `sourceEwpFile: VirtualFile`.
- If the user changes the active EWP file in the combo box during a drag gesture, or drags across different projects, `canImport` validates `data.ewpFile == currentEwp` and rejects the drop.

---

## 4. Technical Architecture & Threading

### Component Interaction

```
[ SimpleTree (JTree) ]
         │
         ├── transferHandler = EwpTreeTransferHandler
         │        ├── createTransferable() -> EwpTreeTransferable(EwpDragData)
         │        ├── canImport()          -> validates rules; repaints drop highlight
         │        └── importData()         -> executes moveNodes; invokes callback
         │
         ├── EwpFileWriter.moveNodes()
         │        ├── WriteCommandAction (run on EDT / Write Action)
         │        ├── moveNodesInXml()
         │        │     ├── moveFileInXml()
         │        │     ├── moveGroupInXml()
         │        │     └── EwpDomSortFixer.sortAndFormat()
         │        └── FileDocumentManager.saveDocument()
         │
         └── onNodesMoved()
                  ├── indexService.invalidate()
                  └── reloadTree(selectGroupPath)
```

### Windows AWT/OLE Threading Fix

Under Windows, standard Swing `TransferHandler` interactions with the native OLE drop target (`WDropTargetContextPeer`) can cause deadlocks or `InvalidDnDOperationException` / `RPC_E_CANTCALLOUT_ININPUTSYNCCALL` when `getTransferData()` is called during continuous `dragOver` events.

To ensure responsive and glitch-free drag-and-drop:

1. **JVM-Local In-Memory Data Flavor**:
   ```kotlin
   val DATA_FLAVOR = DataFlavor(
       "${DataFlavor.javaJVMLocalObjectMimeType};class=\"${EwpDragData::class.java.name}\"",
       "EWP Drag Nodes",
       EwpDragData::class.java.classLoader,
   )
   ```
2. **Active Drag Memory Reference**:
   - `EwpTreeTransferHandler.activeDragData` maintains a volatile reference to the current drag session.
   - `canImport()` inspects `activeDragData` directly without triggering native OLE clipboard queries during mouse movement.
   - Cleaned up in `importData()` and `exportDone()`.
3. **Explicit Drop Location Repainting**:
   - `support.setShowDropLocation(valid)` is explicitly called in `canImport()`.
   - `(support.component as? JTree)?.repaint()` ensures visual feedback updates without starvation.
   - `EwpTreeCellRenderer` sets `setPaintFocusBorder(true)` when hovering over a drop target row.
4. **EDT Dispatch Separation**:
   - `onNodesMoved` is deferred via `SwingUtilities.invokeLater` so the native drop gesture finishes completely before tree reload starts.
   - `reloadTree` and `reloadEwpFiles` are guarded with `SwingUtilities.isEventDispatchThread()` checks.

---

## 5. Automated Tests

- **`EwpFileWriterTest`**:
  - `movesFileToAnotherGroup`: Verifies file relocation and alphabetical re-sorting among siblings.
  - `movesFileToNewGroup`: Verifies creation of target group if absent.
  - `movesFileRejectsSameGroup` & `movesFileRejectsDuplicateInTarget`: Verifies no-ops for invalid file moves.
  - `movesFileRejectsEmptyTargetGroup`: Verifies files cannot be moved to root.
  - `movesGroupToAnotherGroup`: Verifies moving nested group and preserving its contents.
  - `movesGroupToRoot`: Verifies group promotion to root and suffix tag (`<projectSettings>`) preservation.
  - `movesGroupRejectsSelfAndDescendant`: Verifies cycle prevention.
  - `movesGroupRejectsSameParent` & `movesGroupRejectsDuplicateNameInTarget`: Verifies duplicate prevention.
  - `movesMultipleFilesAndGroupsTogether`: Verifies atomic batch moves.

- **`EwpTreeTransferHandlerTest`**:
  - `filterTopLevelPathsKeepsOnlyAncestors` & `filterTopLevelPathsKeepsIndependentSiblings`: Verifies path hierarchy pruning.
  - `resolveDropTargetForGroupNode`, `resolveDropTargetForRootNode`, `resolveDropTargetForFileNodeResolvesToParentGroup`: Verifies drop target resolution.
  - `isValidDrop...`: Comprehensive test cases for valid and invalid file and group drop scenarios.
  - `transferableUsesJvmLocalObjectFlavor`: Verifies local JVM MIME type configuration.
  - `activeDragDataTracksCurrentDragLifecycle`: Verifies lifecycle state cleanup.
