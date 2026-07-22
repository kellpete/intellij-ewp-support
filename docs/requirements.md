# Plugin description

I want to create a plugin for Clion, that adds support for IAR EWARMs project files. The EWARM project files have ewp
extension and contain xml.

## Requirements

I want to create a plugin that will:

- Offer a view that visualizes (for a selected ewp) the groups including subgroups and files, as a hierarchical
  structure
- Offers a view that shows which ewp files include the currently opened file, and which ewps don't. Allow to quickly
  add/remove it from these ewps
- Makes sure the alphabetical sorting stays intact on changes. 
- Warn if sorting is incorrect, and offer to fix it.

Nice to haves:

- Updates the ewp file when a file is created/copied/moved/renamed with Clion tools
- Allows to add files or subgroups to a group in the ewp project file (nice to have)
- When creating a class in Clion, offer to add the new files to the ewp too, just like it is supported with Cmakelists
- When deleting a file, remove it also from ewp. Also remove empty groups.

There might be multiple ewp files in a project, each ewp referring to a (possibly overlapping) set of source files.
Ideally, all these ewp files in the project tree should be kept up to date (see requirements regarding updates, adding
files, etc). This is again similar to multiple targets in cmakelists.

## EWP file structure and formatting rules

Inside the xml file, on path project/group there is a hierarchical view of the project's
source files. Each group contains any number of subgroups and files.  The placeholder `$PROJ_DIR$` refers to the
directory that contains the ewp file.

When changing the ewp file (e.g. to fix sorting), make sure to not introduce unnecessary changes. In particular:
- Do not use self-closing tags (use `<state></state>` instead of `<state />`)
- Do not remove the header `<?xml version="1.0" encoding="UTF-8"?>`
- Do not remove the trailing linebreak (there is a linebrak after `</project>`)
- Us 4 spaces per indentation level

## Sorting

Files and groups in the ewp must be sorted according to following rules.

In each group:
- Subgroups come first and are ordered alphabetically 
- Files come second and are also ordered alphabetically
  - Only the filename component is relevant for the sorting. E.g. for `$PROJ_DIR$\..\config\aaa.txt`, only `aaa.txt` is
    relevant for the sorting

## Mapping of directory structure to ewp groups

The group hierarchy is similar to the directory hierarchy, but not equal. To determine the correct ewp group for a
directory, I propose:

1. Check if the ewp contains files in the given directory. If yes, use this existing mapping. (Let's call this the
   obvious mapping)
2. Else, do a recursive search on the parent directories until the project root is reached. If one of the parent
   directories has an obvious mapping, use it and add subgroups for the subfolders.
3. If this produces no mapping, take the shortest file path in the ewp, and build a mapping from the deepest common
   directory.
4. If that also fails, create ewp groups that match the filesystem directories

### Example 1:

Given /src/main.cpp and its existing ewp group "/app"

New file /src/tools/helper.cpp: No obvious mapping for "tools" dir. So parent dir is checked - "/src". This has an
obvious mapping since it contains a file "main.cpp" that is in group "app". So helper.cpp shall go to group app/tools
(tools is a new subgroup in app)

### Example 2:

Given /src/app/main.cpp and its existing ewp group "source/application"

New file /src/tools/helper.cpp: No obvious mapping for "tools" dir. So parent dir is checked - "/src". This contains no
files. Also the project root "/" contains no files. Therefore, rule 3 is used. Shortest filepath in ewp is
/src/app/main.cpp, so /src/app maps to /source/application. The deepest common dir is /src, with ewp group mapping
/source. Therefore, the mapping of directory /src/tools/ is ewp group /source/tools

## Questions:

- What would be the best way to visualize the ewp groups and files? A side panel is an obvious choice, but maybe there
  are others?
- Which of the listed requirements are achievable?

## Example ewp file:

```
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <fileVersion>4</fileVersion>
    <configuration>
        <name>Debug</name>
        <toolchain>
            <name>ARM</name>
        </toolchain>
        <debug>1</debug>
        <settings>
            <name>General</name>
            <archiveVersion>3</archiveVersion>
            <!-- Removed: Many options, for compiler, linker etc-->
        </settings>
    </configuration>
    <group>
        <name>config</name>
        <group>
            <name>ra6m5</name>
            <file>
                <name>$PROJ_DIR$\..\config\ra6m5\option_setting_memory_init.h</name>
            </file>
        </group>
        <file>
            <name>$PROJ_DIR$\..\config\NodeSettings.hpp</name>
        </file>
        <file>
            <name>$PROJ_DIR$\..\config\ThreadSettings.hpp</name>
        </file>
    </group>
    <group>
        <name>src</name>
        <group>
            <name>services</name>
            <group>
                <name>parameter</name>
                <file>
                    <name>$PROJ_DIR$\..\src\services\parameter\RedundantParameters.cpp</name>
                </file>
                <file>
                    <name>$PROJ_DIR$\..\src\services\parameter\RedundantParameters.hpp</name>
                </file>
            </group>
        </group>
        <file>
            <name>$PROJ_DIR$\..\src\main.cpp</name>
        </file>
    </group>
    <projectSettings>
        <general>
            <resolveSourceForBinaries>true</resolveSourceForBinaries>
        </general>
    </projectSettings>
</project>
```



