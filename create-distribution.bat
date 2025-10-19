@echo off
echo ===============================================
echo Building Tarmin2 Distribution
echo ===============================================
echo.

echo [1/3] Cleaning previous builds...
call .\gradlew.bat clean

echo [2/3] Building JAR file...
call .\gradlew.bat lwjgl3:jar

if errorlevel 1 (
    echo.
    echo ERROR: Build failed!
    pause
    exit /b 1
)

echo [3/3] Creating distribution folder...
if exist "distribution" rmdir /s /q "distribution"
mkdir "distribution"

echo Copying JAR file...
for %%f in (lwjgl3\build\libs\*.jar) do (
    copy "%%f" "distribution\Tarmin2.jar"
)

echo Creating launcher script...
(
echo @echo off
echo title Tarmin2
echo echo ===============================================
echo echo            Starting Tarmin2
echo echo ===============================================
echo echo.
echo java -jar Tarmin2.jar
echo if errorlevel 1 ^(
echo     echo.
echo     echo ===============================================
echo     echo ERROR: Failed to start the game
echo     echo ===============================================
echo     echo.
echo     echo Possible reasons:
echo     echo 1. Java is not installed
echo     echo 2. Java version is too old ^(need Java 17+^)
echo     echo.
echo     echo To check Java version:
echo     echo   Open Command Prompt and type: java -version
echo     echo.
echo     pause
echo ^)
) > "distribution\Launch-Tarmin2.bat"

echo Creating README...
(
echo ===============================================
echo              TARMIN2 - README
echo ===============================================
echo.
echo HOW TO RUN:
echo   Double-click "Launch-Tarmin2.bat"
echo.
echo REQUIREMENTS:
echo   - Java 17 or higher
echo   - Download from: https://adoptium.net/
echo.
echo TROUBLESHOOTING:
echo   If the game doesn't start:
echo   1. Open Command Prompt
echo   2. Type: java -version
echo   3. Make sure it says version 17 or higher
echo.
echo   If you see an error about Java not being found:
echo   - Install Java from https://adoptium.net/
echo   - Choose Java 17 or higher
echo.
echo ===============================================
) > "distribution\README.txt"

echo.
echo ===============================================
echo BUILD SUCCESSFUL!
echo ===============================================
echo.
echo Your game is ready in the 'distribution' folder
echo.
echo Contents:
echo   - Tarmin2.jar          ^(the game^)
echo   - Launch-Tarmin2.bat   ^(easy launcher^)
echo   - README.txt           ^(instructions^)
echo.
echo TO DISTRIBUTE:
echo   1. Zip the entire 'distribution' folder
echo   2. Share the zip file
echo   3. Recipients extract and run Launch-Tarmin2.bat
echo.
echo NOTE: Players need Java 17+ installed
echo.
pause
