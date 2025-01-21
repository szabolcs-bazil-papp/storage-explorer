In the project root execute:

java -jar lib\jflex-1.4.1.jar .\swing\src\main\java\com\aestallon\storageexplorer\swing\ui\commander\arcscript\ArcScriptTokenMaker.flex

Follow the instructions in the flex file's doc comment:
1. Remove the second occurrence of the [[[zzRefill]]] and [[[yyReset]]] methods
2. Change the initialisation of [[[zzBuffer]]] to uninitialized.
