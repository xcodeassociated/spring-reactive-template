#!/bin/bash

# SSH Tunnel Manager Script
# Manages multiple SSH tunnels using screen sessions
# Compatible with macOS default bash (3.2+)

# Configuration file path
CONFIG_FILE="${CONFIG_FILE:-./tunnels.conf}"

# Load tunnel configurations from file
load_config() {
    if [ ! -f "$CONFIG_FILE" ]; then
        echo -e "${RED}Error: Configuration file '$CONFIG_FILE' not found${NC}"
        echo -e "${YELLOW}Create a file with tunnel configurations in this format:${NC}"
        echo "tunnel1:2222:localhost:22:user@remote-server1.com"
        echo "tunnel2:3306:localhost:3306:user@remote-server2.com"
        echo "tunnel3:8080:localhost:80:user@remote-server3.com"
        echo ""
        echo -e "${YELLOW}You can also set a custom config file path with:${NC}"
        echo "export CONFIG_FILE=/path/to/your/tunnels.conf"
        exit 1
    fi

    # Read non-empty lines that don't start with #
    TUNNEL_CONFIGS=()
    while IFS= read -r line; do
        # Skip empty lines and comments
        if [[ -n "$line" && ! "$line" =~ ^[[:space:]]*# ]]; then
            TUNNEL_CONFIGS+=("$line")
        fi
    done < "$CONFIG_FILE"

    if [ ${#TUNNEL_CONFIGS[@]} -eq 0 ]; then
        echo -e "${RED}Error: No valid tunnel configurations found in '$CONFIG_FILE'${NC}"
        exit 1
    fi
}

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to check if screen is installed
check_screen() {
    if ! command -v screen >/dev/null 2>&1; then
        echo -e "${RED}Error: screen is not installed. Install it with: brew install screen${NC}"
        exit 1
    fi
}

# Function to parse tunnel config by name
get_tunnel_config() {
    local name=$1
    for config in "${TUNNEL_CONFIGS[@]}"; do
        local tunnel_name=$(echo "$config" | cut -d: -f1)
        if [ "$tunnel_name" = "$name" ]; then
            echo "$config"
            return 0
        fi
    done
    return 1
}

# Function to check if tunnel name exists
tunnel_exists() {
    local name=$1
    for config in "${TUNNEL_CONFIGS[@]}"; do
        local tunnel_name=$(echo "$config" | cut -d: -f1)
        if [ "$tunnel_name" = "$name" ]; then
            return 0
        fi
    done
    return 1
}

# Function to build SSH command from config
build_ssh_command() {
    local config=$1
    local local_port=$(echo "$config" | cut -d: -f2)
    local remote_host=$(echo "$config" | cut -d: -f3)
    local remote_port=$(echo "$config" | cut -d: -f4)
    local ssh_server=$(echo "$config" | cut -d: -f5)

    echo "ssh -L ${local_port}:${remote_host}:${remote_port} ${ssh_server}"
}

# Function to start a single tunnel
start_tunnel() {
    local name=$1
    local config=$(get_tunnel_config "$name")

    if [ $? -ne 0 ]; then
        echo -e "${RED}Error: Tunnel '$name' not found in configuration${NC}"
        return 1
    fi

    if screen -list | grep -q "ssh_tunnel_${name}"; then
        echo -e "${YELLOW}Tunnel '${name}' is already running${NC}"
        return 1
    fi

    local ssh_cmd=$(build_ssh_command "$config")

    echo -e "${BLUE}Starting tunnel: ${name}${NC}"
    echo -e "${BLUE}Command: ${ssh_cmd}${NC}"

    # Start SSH tunnel in a detached screen session
    screen -dmS "ssh_tunnel_${name}" bash -c "$ssh_cmd"

    # Give it a moment to establish
    sleep 2

    # Check if the screen session is still running
    if screen -list | grep -q "ssh_tunnel_${name}"; then
        echo -e "${GREEN}✓ Tunnel '${name}' started successfully${NC}"
        return 0
    else
        echo -e "${RED}✗ Failed to start tunnel '${name}'${NC}"
        return 1
    fi
}

# Function to stop a single tunnel
stop_tunnel() {
    local name=$1

    if ! tunnel_exists "$name"; then
        echo -e "${RED}Error: Tunnel '$name' not found in configuration${NC}"
        return 1
    fi

    if ! screen -list | grep -q "ssh_tunnel_${name}"; then
        echo -e "${YELLOW}Tunnel '${name}' is not running${NC}"
        return 1
    fi

    echo -e "${BLUE}Stopping tunnel: ${name}${NC}"
    screen -S "ssh_tunnel_${name}" -X quit

    sleep 1

    if ! screen -list | grep -q "ssh_tunnel_${name}"; then
        echo -e "${GREEN}✓ Tunnel '${name}' stopped successfully${NC}"
        return 0
    else
        echo -e "${RED}✗ Failed to stop tunnel '${name}'${NC}"
        return 1
    fi
}

# Function to show tunnel status
show_status() {
    echo -e "${BLUE}SSH Tunnel Status:${NC}"
    echo "===================="

    for config in "${TUNNEL_CONFIGS[@]}"; do
        local name=$(echo "$config" | cut -d: -f1)
        if screen -list | grep -q "ssh_tunnel_${name}"; then
            echo -e "${name}: ${GREEN}RUNNING${NC}"
        else
            echo -e "${name}: ${RED}STOPPED${NC}"
        fi
    done

    echo ""
    echo -e "${BLUE}Active screen sessions:${NC}"
    screen -list | grep "ssh_tunnel_" || echo "None"
}

# Function to start all tunnels
start_all() {
    echo -e "${BLUE}Starting all SSH tunnels...${NC}"
    for config in "${TUNNEL_CONFIGS[@]}"; do
        local name=$(echo "$config" | cut -d: -f1)
        start_tunnel "$name"
    done
}

# Function to stop all tunnels
stop_all() {
    echo -e "${BLUE}Stopping all SSH tunnels...${NC}"
    for config in "${TUNNEL_CONFIGS[@]}"; do
        local name=$(echo "$config" | cut -d: -f1)
        stop_tunnel "$name"
    done
}

# Function to connect to a tunnel's screen session
connect_tunnel() {
    local name=$1

    if ! tunnel_exists "$name"; then
        echo -e "${RED}Error: Tunnel '$name' not found in configuration${NC}"
        return 1
    fi

    if ! screen -list | grep -q "ssh_tunnel_${name}"; then
        echo -e "${RED}Tunnel '${name}' is not running${NC}"
        return 1
    fi

    echo -e "${BLUE}Connecting to tunnel '${name}' screen session...${NC}"
    echo -e "${YELLOW}Press Ctrl+A then D to detach from the session${NC}"
    sleep 2
    screen -r "ssh_tunnel_${name}"
}

# Function to show help
show_help() {
    echo "SSH Tunnel Manager"
    echo "=================="
    echo "Usage: $0 [command] [tunnel_name]"
    echo ""
    echo "Commands:"
    echo "  start [name]    - Start specific tunnel or all if no name given"
    echo "  stop [name]     - Stop specific tunnel or all if no name given"
    echo "  restart [name]  - Restart specific tunnel or all if no name given"
    echo "  status          - Show status of all tunnels"
    echo "  connect [name]  - Connect to tunnel's screen session"
    echo "  list            - List available tunnel configurations"
    echo "  help            - Show this help message"
    echo ""
    echo "Available tunnels:"
    for config in "${TUNNEL_CONFIGS[@]}"; do
        local name=$(echo "$config" | cut -d: -f1)
        local ssh_cmd=$(build_ssh_command "$config")
        echo "  ${name}: ${ssh_cmd}"
    done
}

# Function to list available tunnels
list_tunnels() {
    echo -e "${BLUE}Available tunnel configurations:${NC}"
    echo "================================"
    for config in "${TUNNEL_CONFIGS[@]}"; do
        local name=$(echo "$config" | cut -d: -f1)
        local ssh_cmd=$(build_ssh_command "$config")
        echo -e "${GREEN}${name}${NC}: ${ssh_cmd}"
    done
}

# Main script logic
check_screen
load_config

case "${1:-help}" in
    "start")
        if [ -n "$2" ]; then
            if tunnel_exists "$2"; then
                start_tunnel "$2"
            else
                echo -e "${RED}Error: Tunnel '$2' not found${NC}"
                exit 1
            fi
        else
            start_all
        fi
        ;;
    "stop")
        if [ -n "$2" ]; then
            if tunnel_exists "$2"; then
                stop_tunnel "$2"
            else
                echo -e "${RED}Error: Tunnel '$2' not found${NC}"
                exit 1
            fi
        else
            stop_all
        fi
        ;;
    "restart")
        if [ -n "$2" ]; then
            if tunnel_exists "$2"; then
                stop_tunnel "$2"
                sleep 2
                start_tunnel "$2"
            else
                echo -e "${RED}Error: Tunnel '$2' not found${NC}"
                exit 1
            fi
        else
            stop_all
            sleep 3
            start_all
        fi
        ;;
    "status")
        show_status
        ;;
    "connect")
        if [ -n "$2" ]; then
            if tunnel_exists "$2"; then
                connect_tunnel "$2"
            else
                echo -e "${RED}Error: Tunnel '$2' not found${NC}"
                exit 1
            fi
        else
            echo -e "${RED}Error: Please specify tunnel name${NC}"
            show_help
            exit 1
        fi
        ;;
    "list")
        list_tunnels
        ;;
    "help"|*)
        show_help
        ;;
esac