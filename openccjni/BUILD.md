# OpenccJNI Build Instructions

This document explains how to build and package the JNI wrappers (`OpenccWrapper`, `ZhoWrapper`) across platforms.

---

## 1. Compile Test Programs

### Link with `libmath`
```bash
gcc -o use_math_c use_math_c.c -I include -L . -lmath
g++ -o use_math_cpp use_math_cpp.cpp -I . -L . -lmath
```

### Link with `opencc_fmmseg_capi`
```bash
gcc -o use_math_c use_math_c.c -I . -L . -lopencc_fmmseg_capi
g++ -o use_math_cpp use_math_cpp.cpp -I . -L . -lopencc_fmmseg_capi

gcc -o use_opencc_fmmseg_c use_opencc_fmmseg_c.c -I . -L . -lopencc_fmmseg_capi
g++ -o use_opencc_fmmseg_cpp use_opencc_fmmseg_cpp.cpp -I . -L . -lZho_fmmseg_capi
```

---

## 2. Generate JNI Headers

- **JDK ≤ 8**
  ```bash
  javac ZhoWrapper.java
  javah -jni ZhoWrapper
  ```

- **JDK ≥ 9**
  ```bash
  javac -h . ZhoWrapper.java
  javac -h . OpenccWrapper.java
  ```

---

## 3. Compile JNI Wrappers

### Windows
```powershell
g++ -shared -o ZhoWrapper.dll ZhoWrapper.cpp `
  -I . `
  -I "C:\Program Files\Java\jdk-17\include" `
  -I "C:\Program Files\Java\jdk-17\include\win32" `
  -L . -lopencc_fmmseg_capi

g++ -shared -o OpenccWrapper.dll OpenccWrapper.cpp `
  -I . `
  -I "C:\Program Files\Java\jdk-21\include" `
  -I "C:\Program Files\Java\jdk-21\include\win32" `
  -L . -lopencc_fmmseg_capi `
  -static-libstdc++ -static-libgcc -s
```

### Linux
```bash
g++ -shared -o libZhoWrapper.so ZhoWrapper.cpp   -I .   -I "$HOME/.jdks/corretto-21.0.2/include"   -I "$HOME/.jdks/corretto-21.0.2/include/linux"   -L . -lopencc_fmmseg_capi   -Wl,-rpath,'$ORIGIN'

g++ -shared -o libOpenccWrapper.so OpenccWrapper.cpp   -I .   -I "$HOME/.jdks/graalvm-jdk-21.0.6/include"   -I "$HOME/.jdks/graalvm-jdk-21.0.6/include/linux"   -L . -lopencc_fmmseg_capi   -Wl,-rpath,'$ORIGIN'
```

### macOS
```bash
clang++ -dynamiclib   -o libZhoWrapper.dylib ZhoWrapper.cpp   -I .   -I "/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/include"   -I "/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/include/darwin"   -L . -lopencc_fmmseg_capi   -Wl,-rpath,@loader_path   -Wl,-install_name,@rpath/libZhoWrapper.dylib

clang++ -dynamiclib   -o libOpenccWrapper.dylib OpenccWrapper.cpp   -I .   -I "/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/include"   -I "/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/include/darwin"   -L . -lopencc_fmmseg_capi   -Wl,-rpath,@loader_path   -Wl,-install_name,@rpath/libOpenccWrapper.dylib
```

---

## 4. Java Build & Run

```bash
javac -encoding UTF-8 ZhoWrapper.java
javac -encoding UTF-8 OpenccWrapper.java
javac -encoding UTF-8 UseZhoWrapper.java

java UseZhoWrapper
```

---

## 5. Packaging

### Create JAR
```bash
javac ZhoWrapper.java
jar cvf ZhoWrapper.jar ZhoWrapper.class
```

Windows example:
```powershell
& "C:\Program Files\Java\jdk-17\bin\jar.exe" cvf ZhoWrapper.jar ZhoWrapper.class
```

### Run with Package
1. Place `ZhoWrapper.dll` (or `.so`/`.dylib`) and `opencc_fmmseg_capi` in your `java.library.path`.
2. Ensure `ZhoWrapper.class` is on the classpath.

Example:
```bash
java openccwrapper/UseOpenCCWrapper
```

Linux requires copying `.so` libraries into `java.library.path`.

---
