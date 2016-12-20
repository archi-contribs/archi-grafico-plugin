archi-grafico-plugin
====================

### **G**it f**r**iendly **A**rchi **Fi**le **Co**llection
GRAFICO is a way to persist an ArchiMate model in a bunch of XML files (one file per ArchiMate element or view). The resulting files can then be easily tracked and merge using almost any Version Control System (VCS) or Source Code Management (SCM) solutions like [git](http://git-scm.com). Said differently, this is the basis of a really powerfull model repository for Archi.

#### How to use it?
Important remark: this plugin is still young, so be prepared to find some bugs. You should also be aware that generated XML will certainly change a little soon to be even easier to update manually in case of conflict.

Still reading and not afraid? So just [download the plugin](https://github.com/archi-contribs/archi-grafico-plugin/releases) and put it in the 'plugin/' subdirectory of Archi. You should now see two new menu entries "File > Export > Model as GRAFICO..." and "File > Import > Model from GRAFICO...". By itself, the plugin doesn't do any versioning, it's up to you to choose the best tool for you needs (but I highly recommend git).

#### Credit
The root idea came from [some discussions](https://groups.google.com/forum/?hl=en#!searchin/archi-dev/git/archi-dev/8sCoD6Ctj-c/MnqM_luHJRAJ) with Árpád Magosányi and Michael Tapp on the old Archi forum.

#### License
Some will cry, but we decided to not use a real OpenSource license for this plugin: we choose the [Creative Commons Attribution-NonCommercial-NoDerivatives 4.0 International](http://creativecommons.org/licenses/by-nc-nd/4.0/). Why? Simply because despite all the work done on Archi, very few people decided to [donate](http://www.archimatetool.com/#donate). Model repository is the #1 request and we think that this plugin is the basis for such feature and could exist in a (commercial) enhanced version in the future.

So basically:
 * You are free to copy and redistribute this plugin in any medium or format.
 * You must give appropriate credit, provide a link to the license, and indicate if changes were made. You may do so in any reasonable manner, but not in any way that suggests we endorse you or your use.
 * You can't use this plugin for commercial purposes.
 * If you remix, transform, or build upon this plugin, you can't distribute the modified material.

#### Use-cases
This plugin can be used for several purposes (merging models, keeping changelog/history of model edits, multi-user repository...). Have a look on [the dedicated wiki page](https://github.com/archi-contribs/archi-grafico-plugin/wiki/Use-Cases).
