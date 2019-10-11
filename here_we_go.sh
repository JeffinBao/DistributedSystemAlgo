#!/bin/sh

#file_root_path=$(pwd)
# find all java source files
find . -name "*.java" > sources_java.txt
# print all java source file names
cat sources_java.txt
# compile all java source files
javac @sources_java.txt

osascript <<END
tell application "iTerm2"
  tell current session of current window
    tell application "System Events" to keystroke "d" using {command down, shift down}
    tell application "System Events" to key code 126 using {command down, option down}
    # create servers
    repeat with i from 0 to 2
      tell application "System Events" to keystroke "d" using {command down}
      # delay is important, it lets the window initialize and be able to execute following command
      delay 0.5
      write text "cd ./src/main/java"
      write text "java MainApp server " & i
    end repeat
    # move down
    tell application "System Events" to key code 125 using {command down, option down}
    # create clients
    delay 0.5
    write text "cd ./src/main/java"
    write text "java MainApp client 0"
    repeat with i from 0 to 3
      tell application "System Events" to keystroke "d" using {command down}
      delay 0.5
      write text "cd ./src/main/java"
      write text "java MainApp client " & (i+1)
    end repeat
    # init all connections by executing "connect" and moving left
    repeat with i from 0 to 3
      delay 0.5
      write text "connect"
      tell application "System Events" to keystroke "[" using {command down}
    end repeat
    delay 0.5
    write text "connect"

    # start execution
    repeat with i from 0 to 3
      delay 0.5
      write text "start"
      tell application "System Events" to keystroke "]" using {command down}
    end repeat
    delay 0.5
    write text "start"
  end tell
end tell
END



