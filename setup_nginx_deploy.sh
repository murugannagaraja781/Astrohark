#!/bin/bash

# astrohark - Nginx Auto Setup & Deployment Script
# Targeted for Ubuntu/Linux Server

echo "=========================================="
echo "    astrohark Nginx Auto Setup"
echo "=========================================="

APP_DIR="/var/www/Astrohark"
APP_NAME="astro-app"
DEFAULT_PORT=3000

# Step 1: System Check & Git Installation
echo "[1/7] Ensuring Git is installed..."
if ! command -v git &> /dev/null; then
    echo "Git not found. Installing..."
    sudo apt-get update -y
    sudo apt-get install git -y
else
    echo "✓ Git is already installed."
fi

# Step 2: Node.js & PM2 Check
echo "[2/7] Ensuring Node.js & PM2 are installed..."
if ! command -v node &> /dev/null; then
    echo "Node.js not found. Installing Node.js 18..."
    curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
    sudo apt-get install -y nodejs
else
    echo "✓ Node.js $(node -v) is already installed."
fi

if ! command -v pm2 &> /dev/null; then
    echo "PM2 not found. Installing..."
    sudo npm install -g pm2
else
    echo "✓ PM2 is already installed."
fi

# Step 3: Nginx Installation
echo "[3/7] Ensuring Nginx is installed..."
if ! command -v nginx &> /dev/null; then
    echo "Nginx not found. Installing..."
    sudo apt-get update -y
    sudo apt-get install nginx -y
else
    echo "✓ Nginx is already installed."
fi

# Step 4: Clone or Pull Code
echo "[4/7] Getting latest code from Repository..."
if [ ! -d "$APP_DIR" ]; then
    echo "Cloning repository into $APP_DIR..."
    sudo mkdir -p /var/www
    sudo chown $USER:$USER /var/www
    cd /var/www
    git clone https://github.com/murugannagaraja781/Astrohark.git $APP_DIR
else
    echo "Updating existing code in $APP_DIR..."
    cd $APP_DIR
    git reset --hard
    git pull origin main
fi

# Step 5: Install App Dependencies & Handle .env
echo "[5/7] Installing app dependencies..."
cd $APP_DIR

# Determine PORT from .env if possible
PORT=$DEFAULT_PORT
if [ -f ".env" ]; then
    ENV_PORT=$(grep "^PORT=" .env | cut -d '=' -f 2 | tr -d '\r')
    if [ ! -z "$ENV_PORT" ]; then
        PORT=$ENV_PORT
    fi
fi
echo "Using port: $PORT"

# Optimization for low memory npm
export NODE_OPTIONS="--max-old-space-size=448"
npm install --production --no-audit --no-fund --prefer-offline || {
    echo "npm install failed. Retrying with --no-package-lock..."
    rm -rf node_modules
    npm install --production --no-audit --no-fund --no-package-lock
}

# Step 6: Configure Nginx as Reverse Proxy
echo "[6/7] Configuring Nginx reverse proxy..."

NGINX_CONF="/etc/nginx/sites-available/astrohark"

sudo bash -c "cat > $NGINX_CONF <<EOF
server {
    listen 80;
    server_name _;

    location / {
        proxy_pass http://localhost:$PORT;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \\\$http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host \\\$host;
        proxy_cache_bypass \\\$http_upgrade;

        # Extended timeouts for WebRTC signaling and long-running APIs
        proxy_read_timeout 3600s;
        proxy_connect_timeout 3600s;
        proxy_send_timeout 3600s;
    }

    # Static uploads folder for profile pictures, etc.
    location /uploads/ {
        alias $APP_DIR/uploads/;
        expires 30d;
        add_header Cache-Control \"public, no-transform\";
    }
}
EOF"

# Enable the site and remove default
sudo ln -sf $NGINX_CONF /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default

# Test and reload Nginx
if sudo nginx -t; then
    sudo systemctl restart nginx
    echo "✓ Nginx configured and restarted."
else
    echo "✗ Nginx configuration test failed. Skipping restart."
fi

# Step 7: Start App with PM2
echo "[7/7] Starting application with PM2..."
cd $APP_DIR
pm2 delete $APP_NAME 2>/dev/null || true
pm2 start server.js --name $APP_NAME
pm2 save
pm2 startup | grep "sudo" | bash || true

echo ""
echo "=========================================="
echo "    Setup & Deployment Complete! ✨"
echo "    App is live on http://localhost (Port 80 via Nginx Proxy)"
echo "=========================================="
echo "PM2 Status: pm2 status"
echo "App Logs:   pm2 logs $APP_NAME"
echo "Nginx Status: sudo systemctl status nginx"
echo "=========================================="
