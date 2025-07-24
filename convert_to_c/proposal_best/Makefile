# ============================================================================
# Makefile para Analizador de Redes de Petri en C - Propuesta Óptima
# ============================================================================

# Información del proyecto
PROJECT_NAME = reachability-analyzer
VERSION = 1.0.0

# Compilador y herramientas
CC = gcc
CXX = g++
AR = ar
STRIP = strip

# Directorios
SRCDIR = src
INCDIR = include  
OBJDIR = obj
BINDIR = bin
TESTDIR = tests
DOCDIR = docs
DISTDIR = dist

# Archivos fuente
SOURCES = $(wildcard *.c)
HEADERS = $(wildcard *.h)
OBJECTS = $(SOURCES:%.c=$(OBJDIR)/%.o)
TARGET = $(BINDIR)/$(PROJECT_NAME)

# Configuración del compilador
CSTANDARD = -std=c11
WARNINGS = -Wall -Wextra -Wpedantic -Wformat=2 -Wstrict-prototypes \
           -Wmissing-prototypes -Wold-style-definition -Wno-unused-parameter
INCLUDES = -I. -I$(INCDIR)

# Flags base
BASE_CFLAGS = $(CSTANDARD) $(WARNINGS) $(INCLUDES) -pthread
BASE_LDFLAGS = -pthread -lm

# Dependencias del sistema
SYSTEM_LIBS = -lcjson
OPTIONAL_LIBS = 

# Detección automática de características del sistema
UNAME_S := $(shell uname -s)
UNAME_M := $(shell uname -m)
NPROC := $(shell nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)

# Configuración específica por OS
ifeq ($(UNAME_S),Linux)
    SYSTEM_LIBS += -lrt
    OPTIONAL_LIBS += -ljemalloc
    CPU_ARCH = $(UNAME_M)
endif

ifeq ($(UNAME_S),Darwin)
    # macOS específico
    BREW_PREFIX := $(shell brew --prefix 2>/dev/null || echo /usr/local)
    INCLUDES += -I$(BREW_PREFIX)/include
    BASE_LDFLAGS += -L$(BREW_PREFIX)/lib
    CPU_ARCH = $(UNAME_M)
endif

# Configuración de arquitectura
ifeq ($(CPU_ARCH),x86_64)
    ARCH_FLAGS = -march=native -mtune=native
endif

ifeq ($(CPU_ARCH),arm64)
    ARCH_FLAGS = -mcpu=native
endif

# ============================================================================
# CONFIGURACIONES DE COMPILACIÓN
# ============================================================================

# Configuración DEBUG
DEBUG_CFLAGS = $(BASE_CFLAGS) -g3 -O0 -DDEBUG -DENABLE_STATISTICS \
               -fsanitize=address,undefined -fno-omit-frame-pointer
DEBUG_LDFLAGS = $(BASE_LDFLAGS) -fsanitize=address,undefined

# Configuración RELEASE optimizada
RELEASE_CFLAGS = $(BASE_CFLAGS) -O3 -DNDEBUG -flto $(ARCH_FLAGS) \
                 -ffast-math -funroll-loops -finline-functions
RELEASE_LDFLAGS = $(BASE_LDFLAGS) -flto -s

# Configuración PROFILE
PROFILE_CFLAGS = $(BASE_CFLAGS) -O2 -g -pg -DNDEBUG
PROFILE_LDFLAGS = $(BASE_LDFLAGS) -pg

# Configuración SANITIZE (para testing)
SANITIZE_CFLAGS = $(BASE_CFLAGS) -O1 -g -fsanitize=thread,memory,leak \
                  -fno-omit-frame-pointer
SANITIZE_LDFLAGS = $(BASE_LDFLAGS) -fsanitize=thread,memory,leak

# Configuración con jemalloc (si está disponible)
ifdef JEMALLOC
    ifneq ($(shell pkg-config --exists jemalloc && echo yes),)
        JEMALLOC_CFLAGS = $(shell pkg-config --cflags jemalloc)
        JEMALLOC_LDFLAGS = $(shell pkg-config --libs jemalloc)
        OPTIONAL_LIBS += $(JEMALLOC_LDFLAGS)
        RELEASE_CFLAGS += $(JEMALLOC_CFLAGS) -DUSE_JEMALLOC
    endif
endif

# Configuración final
CFLAGS = $(RELEASE_CFLAGS)
LDFLAGS = $(RELEASE_LDFLAGS) $(SYSTEM_LIBS) $(OPTIONAL_LIBS)

# ============================================================================
# TARGETS PRINCIPALES
# ============================================================================

.PHONY: all clean distclean debug release profile sanitize test benchmark install uninstall help

# Target por defecto
all: release

# Target de release optimizado
release: CFLAGS = $(RELEASE_CFLAGS)
release: LDFLAGS = $(RELEASE_LDFLAGS) $(SYSTEM_LIBS) $(OPTIONAL_LIBS)
release: $(TARGET)
	@echo "✓ Release build completed successfully"
	@echo "  Binary: $(TARGET)"
	@echo "  Size: $$(du -h $(TARGET) | cut -f1)"

# Target de debug
debug: CFLAGS = $(DEBUG_CFLAGS)
debug: LDFLAGS = $(DEBUG_LDFLAGS) $(SYSTEM_LIBS) $(OPTIONAL_LIBS)
debug: $(TARGET)
	@echo "✓ Debug build completed successfully"

# Target de profiling
profile: CFLAGS = $(PROFILE_CFLAGS)
profile: LDFLAGS = $(PROFILE_LDFLAGS) $(SYSTEM_LIBS) $(OPTIONAL_LIBS)
profile: $(TARGET)
	@echo "✓ Profile build completed successfully"

# Target con sanitizers
sanitize: CFLAGS = $(SANITIZE_CFLAGS)
sanitize: LDFLAGS = $(SANITIZE_LDFLAGS) $(SYSTEM_LIBS) $(OPTIONAL_LIBS)
sanitize: $(TARGET)
	@echo "✓ Sanitize build completed successfully"

# Target con máxima optimización
release-optimized: CFLAGS = $(RELEASE_CFLAGS) -O3 -fprofile-use=profile.data
release-optimized: profile-generate run-profile-workload $(TARGET)
	@echo "✓ Optimized release build completed successfully"

# ============================================================================
# REGLAS DE COMPILACIÓN
# ============================================================================

# Compilación del ejecutable principal
$(TARGET): $(OBJECTS) | $(BINDIR)
	@echo "🔗 Linking $(TARGET)..."
	$(CC) $(OBJECTS) -o $@ $(LDFLAGS)
	@if [ "$(CFLAGS)" = "$(RELEASE_CFLAGS)" ]; then \
		echo "📦 Stripping symbols..."; \
		$(STRIP) $@; \
	fi

# Compilación de archivos objeto
$(OBJDIR)/%.o: %.c $(HEADERS) | $(OBJDIR)
	@echo "🔨 Compiling $<..."
	$(CC) $(CFLAGS) -c $< -o $@

# Creación de directorios
$(OBJDIR):
	@mkdir -p $(OBJDIR)

$(BINDIR):
	@mkdir -p $(BINDIR)

$(DISTDIR):
	@mkdir -p $(DISTDIR)

# ============================================================================
# TARGETS DE LIMPIEZA
# ============================================================================

clean:
	@echo "🧹 Cleaning build artifacts..."
	rm -rf $(OBJDIR) $(BINDIR)
	rm -f *.gcda *.gcno *.gcov gmon.out profile.data
	rm -f core.* vgcore.*

distclean: clean
	@echo "🧹 Deep cleaning..."
	rm -rf $(DISTDIR) $(DOCDIR)/html
	rm -f *.log *.dot *.png *.svg
	find . -name "*.orig" -delete
	find . -name "*.rej" -delete

# ============================================================================
# TARGETS DE TESTING Y VALIDACIÓN
# ============================================================================

# Verificar dependencias del sistema
check-deps:
	@echo "🔍 Checking system dependencies..."
	@command -v $(CC) >/dev/null || (echo "❌ Compiler $(CC) not found" && exit 1)
	@pkg-config --exists libcjson || (echo "❌ libcjson not found. Install: apt-get install libcjson-dev" && exit 1)
	@echo "✓ All dependencies satisfied"

# Tests básicos
test: debug
	@echo "🧪 Running basic tests..."
	@if [ -f "../data/net_1.json" ]; then \
		echo "Testing with net_1.json..."; \
		$(TARGET) --input ../data/net_1.json --debug; \
	else \
		echo "⚠️  Test data not found in ../data/"; \
	fi

# Tests de corrección (comparación con Java)
test-correctness: release
	@echo "🔍 Running correctness tests..."
	@if [ -d "../data" ]; then \
		for file in ../data/net_*.json; do \
			if [ -f "$$file" ]; then \
				echo "Testing $$file..."; \
				$(TARGET) --input "$$file" --output "c_result.dot" --omega; \
				echo "✓ Processed $$file successfully"; \
			fi; \
		done; \
	else \
		echo "⚠️  Test data directory not found"; \
	fi

# Tests de memoria
test-memory: debug
	@echo "🔍 Running memory tests..."
	@command -v valgrind >/dev/null || (echo "❌ valgrind not found" && exit 1)
	@if [ -f "../data/net_1.json" ]; then \
		valgrind --tool=memcheck --leak-check=full --show-leak-kinds=all \
		         --track-origins=yes --verbose --log-file=valgrind.log \
		         $(TARGET) --input ../data/net_1.json --omega; \
		echo "✓ Memory test completed. Check valgrind.log for details"; \
	fi

# Tests de concurrencia
test-concurrency: sanitize
	@echo "🔍 Running concurrency tests..."
	@if [ -f "../data/net_4.json" ]; then \
		for threads in 1 2 4 8; do \
			echo "Testing with $$threads threads..."; \
			$(TARGET) --input ../data/net_4.json --nthreads $$threads --omega; \
		done; \
	fi

# Suite completa de tests
test-all: check-deps test-correctness test-memory test-concurrency
	@echo "✅ All tests completed successfully"

# ============================================================================
# TARGETS DE BENCHMARK Y RENDIMIENTO
# ============================================================================

# Benchmark básico
benchmark: release
	@echo "🚀 Running performance benchmark..."
	@if [ -d "../data" ]; then \
		echo "Single-threaded benchmark:"; \
		for file in ../data/net_*.json; do \
			if [ -f "$$file" ]; then \
				echo -n "$$(basename $$file): "; \
				/usr/bin/time -f "%e seconds, %M KB peak memory" \
				$(TARGET) --input "$$file" --omega --nthreads 1 2>/dev/null; \
			fi; \
		done; \
		echo; \
		echo "Multi-threaded benchmark ($(NPROC) threads):"; \
		for file in ../data/net_*.json; do \
			if [ -f "$$file" ]; then \
				echo -n "$$(basename $$file): "; \
				/usr/bin/time -f "%e seconds, %M KB peak memory" \
				$(TARGET) --input "$$file" --omega --nthreads $(NPROC) 2>/dev/null; \
			fi; \
		done; \
	fi

# Benchmark de escalabilidad
benchmark-scaling: release
	@echo "📊 Running scalability benchmark..."
	@if [ -f "../data/net_full.json" ]; then \
		for threads in 1 2 4 8 16; do \
			echo "Testing with $$threads threads:"; \
			/usr/bin/time -v $(TARGET) --input ../data/net_full.json --omega --nthreads $$threads; \
			echo "---"; \
		done; \
	fi

# Profiling con gprof
profile-generate: CFLAGS = $(PROFILE_CFLAGS) -fprofile-generate
profile-generate: $(TARGET)

run-profile-workload: profile-generate
	@if [ -f "../data/net_4.json" ]; then \
		$(TARGET) --input ../data/net_4.json --omega --nthreads 4; \
	fi

# Análisis de rendimiento con perf (Linux)
perf-analysis: release
	@command -v perf >/dev/null || (echo "❌ perf not found (Linux only)" && exit 1)
	@if [ -f "../data/net_full.json" ]; then \
		echo "🔍 Running perf analysis..."; \
		perf record -g $(TARGET) --input ../data/net_full.json --omega; \
		perf report; \
	fi

# ============================================================================
# TARGETS DE DISTRIBUCIÓN E INSTALACIÓN
# ============================================================================

# Instalación en el sistema
install: release
	@echo "📦 Installing $(PROJECT_NAME)..."
	install -d $(DESTDIR)/usr/local/bin
	install -m 755 $(TARGET) $(DESTDIR)/usr/local/bin/
	@echo "✓ Installed to $(DESTDIR)/usr/local/bin/$(PROJECT_NAME)"

# Desinstalación
uninstall:
	@echo "🗑️  Uninstalling $(PROJECT_NAME)..."
	rm -f $(DESTDIR)/usr/local/bin/$(PROJECT_NAME)
	@echo "✓ Uninstalled"

# Crear distribución
distribution: release | $(DISTDIR)
	@echo "📦 Creating distribution package..."
	tar -czf $(DISTDIR)/$(PROJECT_NAME)-$(VERSION)-$(UNAME_S)-$(CPU_ARCH).tar.gz \
		$(TARGET) *.h Makefile README.md
	@echo "✓ Distribution created: $(DISTDIR)/$(PROJECT_NAME)-$(VERSION)-$(UNAME_S)-$(CPU_ARCH).tar.gz"

# Crear paquete .deb (Ubuntu/Debian)
deb: release
	@echo "📦 Creating .deb package..."
	@command -v fpm >/dev/null || (echo "❌ fpm not found. Install: gem install fpm" && exit 1)
	fpm -s dir -t deb -n $(PROJECT_NAME) -v $(VERSION) \
		--description "High-performance Petri net reachability analyzer" \
		--url "https://github.com/example/reachability-analyzer" \
		--license "MIT" \
		$(TARGET)=/usr/local/bin/$(PROJECT_NAME)

# ============================================================================
# TARGETS DE DESARROLLO Y MANTENIMIENTO
# ============================================================================

# Formateo de código
format:
	@command -v clang-format >/dev/null || (echo "❌ clang-format not found" && exit 1)
	@echo "🎨 Formatting code..."
	clang-format -i -style="{BasedOnStyle: Linux, IndentWidth: 4}" *.c *.h

# Análisis estático
lint:
	@command -v cppcheck >/dev/null || (echo "❌ cppcheck not found" && exit 1)
	@echo "🔍 Running static analysis..."
	cppcheck --enable=all --std=c11 --platform=unix64 \
		--suppress=missingIncludeSystem *.c

# Verificar estilo de código
check-style:
	@command -v clang-format >/dev/null || (echo "❌ clang-format not found" && exit 1)
	@echo "🔍 Checking code style..."
	@for file in *.c *.h; do \
		if [ -f "$$file" ]; then \
			clang-format -style="{BasedOnStyle: Linux, IndentWidth: 4}" "$$file" | \
			diff -u "$$file" - || (echo "❌ $$file needs formatting" && exit 1); \
		fi; \
	done
	@echo "✓ All files follow coding style"

# Generar documentación
docs:
	@command -v doxygen >/dev/null || (echo "❌ doxygen not found" && exit 1)
	@echo "📚 Generating documentation..."
	doxygen Doxyfile
	@echo "✓ Documentation generated in $(DOCDIR)/html/"

# ============================================================================
# INFORMACIÓN Y AYUDA
# ============================================================================

# Mostrar información del sistema
info:
	@echo "🖥️  System Information:"
	@echo "  OS: $(UNAME_S)"
	@echo "  Architecture: $(CPU_ARCH)" 
	@echo "  CPU Cores: $(NPROC)"
	@echo "  Compiler: $$($(CC) --version | head -n1)"
	@echo "  Make version: $$(make --version | head -n1)"
	@echo ""
	@echo "📦 Project Information:"
	@echo "  Name: $(PROJECT_NAME)"
	@echo "  Version: $(VERSION)"
	@echo "  Source files: $(words $(SOURCES))"
	@echo "  Header files: $(words $(HEADERS))"

# Ayuda
help:
	@echo "🔨 $(PROJECT_NAME) v$(VERSION) - Makefile Help"
	@echo ""
	@echo "📋 Build Targets:"
	@echo "  all, release     - Build optimized release version"
	@echo "  debug           - Build debug version with sanitizers" 
	@echo "  profile         - Build version with profiling support"
	@echo "  sanitize        - Build with memory/thread sanitizers"
	@echo "  release-optimized - Build with profile-guided optimization"
	@echo ""
	@echo "🧪 Testing Targets:"
	@echo "  check-deps      - Verify system dependencies"
	@echo "  test           - Run basic functionality tests"
	@echo "  test-correctness - Compare results with Java version"
	@echo "  test-memory    - Run memory leak detection"
	@echo "  test-concurrency - Test thread safety"
	@echo "  test-all       - Run complete test suite"
	@echo ""
	@echo "🚀 Performance Targets:"
	@echo "  benchmark      - Run performance benchmarks"
	@echo "  benchmark-scaling - Test scalability across thread counts"
	@echo "  perf-analysis  - Profile with perf (Linux only)"
	@echo ""
	@echo "📦 Distribution Targets:"
	@echo "  install        - Install to system (/usr/local/bin)"
	@echo "  uninstall      - Remove from system"
	@echo "  dist           - Create distribution tarball"
	@echo "  deb            - Create .deb package (requires fpm)"
	@echo ""
	@echo "🛠️  Development Targets:"
	@echo "  format         - Format code with clang-format"
	@echo "  lint           - Run static analysis"
	@echo "  check-style    - Verify code formatting"
	@echo "  docs           - Generate documentation"
	@echo ""
	@echo "🧹 Cleanup Targets:"
	@echo "  clean          - Remove build artifacts"
	@echo "  distclean      - Deep clean (includes generated files)"
	@echo ""
	@echo "ℹ️  Information:"
	@echo "  info           - Show system and project information"
	@echo "  help           - Show this help message"
	@echo ""
	@echo "⚙️  Variables:"
	@echo "  JEMALLOC=1     - Enable jemalloc allocator"
	@echo "  CC=compiler    - Use alternative compiler"
	@echo "  V=1           - Verbose compilation"

# ============================================================================
# CONFIGURACIÓN AVANZADA
# ============================================================================

# Compilación verbose si V=1
ifndef V
.SILENT:
endif

# Evitar borrado de archivos intermedios
.SECONDARY: $(OBJECTS)

# Detectar cambios en headers para recompilación
$(OBJECTS): $(HEADERS)

# ============================================================================
# TARGETS DE EJEMPLO Y DEMO
# ============================================================================

# Ejecutar ejemplo básico
example: release
	@echo "🎯 Running basic example..."
	@if [ -f "../data/net_1.json" ]; then \
		$(TARGET) --input ../data/net_1.json --output example.dot --omega --debug; \
		echo "✓ Example completed. Output in example.dot"; \
	else \
		echo "⚠️  Example data not found"; \
	fi

# Demo de funcionalidades
demo: release
	@echo "🎪 Running demonstration..."
	@echo "1. Basic analysis:"
	@if [ -f "../data/net_1.json" ]; then \
		$(TARGET) --input ../data/net_1.json; \
	fi
	@echo ""
	@echo "2. Analysis with omega detection:"
	@if [ -f "../data/net_1.json" ]; then \
		$(TARGET) --input ../data/net_1.json --omega; \
	fi
	@echo ""
	@echo "3. Multi-threaded analysis:"
	@if [ -f "../data/net_1.json" ]; then \
		$(TARGET) --input ../data/net_1.json --omega --nthreads 4; \
	fi

# Quick test para desarrollo
quick-test: debug
	@if [ -f "../data/net_1.json" ]; then \
		$(TARGET) --input ../data/net_1.json --omega --debug; \
	else \
		echo "⚠️  Quick test data not found"; \
	fi