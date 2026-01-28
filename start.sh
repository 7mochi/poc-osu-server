#!/bin/bash
# Banchus Server Launcher Script with Caddy support
# Compile, build, and run Spring Boot + Caddy with hot reload

set -e

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Directories
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$PROJECT_DIR/target"
SPRING_PID=""
CADDY_PID=""

# Logging functions
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_debug() {
    echo -e "${BLUE}[DEBUG]${NC} $1"
}

# Cleanup on exit
cleanup() {
    echo ""
    log_warn "Stopping services..."
    
    if [ ! -z "$SPRING_PID" ] && ps -p $SPRING_PID > /dev/null 2>&1; then
        log_info "Stopping Spring Boot (PID: $SPRING_PID)..."
        kill $SPRING_PID 2>/dev/null || true
        wait $SPRING_PID 2>/dev/null || true
    fi
    
    if [ ! -z "$CADDY_PID" ] && ps -p $CADDY_PID > /dev/null 2>&1; then
        log_info "Stopping Caddy (PID: $CADDY_PID)..."
        sudo kill $CADDY_PID 2>/dev/null || true
        wait $CADDY_PID 2>/dev/null || true
    fi
    
    log_info "Services stopped ✓"
    exit 0
}

# Trap Ctrl+C
trap cleanup SIGINT SIGTERM

# Find the JAR file (handles git commit ID suffix)
find_jar() {
    local jar=$(ls -t "$BUILD_DIR"/banchus-0.0.1-*.jar 2>/dev/null | head -1)
    if [ -z "$jar" ]; then
        return 1
    fi
    echo "$jar"
}

# Compile project
compile_project() {
    log_info "Compiling project with Maven..."
    cd "$PROJECT_DIR"
    
    if mvn clean package -DskipTests -q; then
        log_info "Compilation successful ✓"
        return 0
    else
        log_error "Compilation failed"
        return 1
    fi
}

# Start Spring Boot
start_spring_boot() {
    local jar=$(find_jar)
    
    if [ -z "$jar" ]; then
        log_error "JAR file not found in $BUILD_DIR"
        exit 1
    fi
    
    log_info "Starting Spring Boot..."
    log_debug "Using JAR: $(basename $jar)"
    
    # Kill any existing instances
    pkill -f "java.*banchus" 2>/dev/null || true
    sleep 1
    
    # Start the JAR
    java -jar "$jar" &
    SPRING_PID=$!
    
    # Wait for startup
    sleep 5
    
    if ps -p $SPRING_PID > /dev/null 2>&1; then
        log_info "Spring Boot started (PID: $SPRING_PID) ✓"
        return 0
    else
        log_error "Failed to start Spring Boot"
        return 1
    fi
}

# Start Caddy
start_caddy() {
    log_info "Starting Caddy..."
    sudo pkill -f caddy 2>/dev/null || true
    sleep 1
    
    if ! [ -f "$PROJECT_DIR/Caddyfile" ]; then
        log_warn "Caddyfile not found, skipping Caddy"
        return 0
    fi
    
    sudo caddy run --config "$PROJECT_DIR/Caddyfile" &
    CADDY_PID=$!
    sleep 2
    
    if ps -p $CADDY_PID > /dev/null 2>&1; then
        log_info "Caddy started (PID: $CADDY_PID) ✓"
        return 0
    else
        log_warn "Failed to start Caddy (may require sudo password)"
        return 0
    fi
}

# Monitor for code changes and reload
monitor_changes() {
    log_info "Monitoring for code changes..."
    
    while true; do
        # Wait for changes in src/
        inotifywait -r -e modify "$PROJECT_DIR/src/" -q 2>/dev/null && {
            echo ""
            log_debug "Code changes detected"
            log_info "Stopping Spring Boot before recompiling..."
            
            # Stop old instance FIRST (before compilation)
            if [ ! -z "$SPRING_PID" ] && ps -p $SPRING_PID > /dev/null 2>&1; then
                log_debug "Stopping Spring Boot instance (PID: $SPRING_PID)..."
                kill $SPRING_PID 2>/dev/null || true
                wait $SPRING_PID 2>/dev/null || true
            fi
            
            # Wait for JAR to be released
            sleep 2
            
            log_info "Recompiling..."
            cd "$PROJECT_DIR"
            if mvn clean package -DskipTests -q; then
                log_info "Recompilation successful ✓"
                
                sleep 1
                
                # Start new instance
                if start_spring_boot; then
                    log_info "Spring Boot restarted successfully ✓"
                else
                    log_error "Failed to restart Spring Boot"
                fi
            else
                log_error "Recompilation failed"
            fi
        }
    done
}

# Main
main() {
    clear
    echo "================================"
    echo -e "${GREEN}🚀 Banchus Server${NC}"
    echo "================================"
    echo ""
    
    # Compile
    if ! compile_project; then
        exit 1
    fi
    
    # Start Spring Boot
    if ! start_spring_boot; then
        exit 1
    fi
    
    # Start Caddy
    start_caddy
    
    echo ""
    echo "================================"
    echo -e "${GREEN}✓ Banchus started${NC}"
    echo "================================"
    echo ""
    echo "Available endpoints:"
    echo "  🔗 http://localhost:8080 (Direct)"
    if [ ! -z "$CADDY_PID" ]; then
        echo "  🔗 https://bancho.local (via Caddy)"
        echo "  🔗 https://osu.bancho.local (via Caddy)"
        echo "  🔗 https://api.bancho.local (via Caddy)"
    fi
    echo ""
    echo -e "${BLUE}Monitoring for changes in src/...${NC}"
    echo -e "${YELLOW}Press Ctrl+C to stop${NC}"
    echo ""
    
    # Monitor for changes
    monitor_changes
}

main
