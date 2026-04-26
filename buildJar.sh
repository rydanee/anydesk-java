rm ./build/*
javac -d build *.java
jar cfe Anydesk-java.jar Main -C build .
chmod +x ./Anydesk-java.jar
