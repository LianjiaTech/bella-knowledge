#!/bin/bash
set -e

# 设置默认值 - 使用简化的路径
ENABLE_SSL=${ENABLE_SSL:-false}
SERVER_DOMAIN=${SERVER_DOMAIN:-localhost}
SSL_CERT_PATH=${SSL_CERT_PATH:-/etc/nginx/ssl/fullchain.pem}
SSL_KEY_PATH=${SSL_KEY_PATH:-/etc/nginx/ssl/private.key}
API_UPSTREAM=${API_UPSTREAM:-http://bella-file-api:8081}
WEB_UPSTREAM=${WEB_UPSTREAM:-http://bella-file-web:3000}

echo "=== 🚀 Bella File API Nginx Configuration ==="
echo "ENABLE_SSL: $ENABLE_SSL"
echo "SERVER_DOMAIN: $SERVER_DOMAIN"
echo "SSL_CERT_PATH: $SSL_CERT_PATH"  
echo "SSL_KEY_PATH: $SSL_KEY_PATH"
echo "API_UPSTREAM: $API_UPSTREAM"
echo "WEB_UPSTREAM: $WEB_UPSTREAM"

if [ "$ENABLE_SSL" = "true" ]; then
    echo "⚡ Configuring HTTPS mode..."
    
    # 检查证书文件是否存在
    if [ -f "$SSL_CERT_PATH" ] && [ -f "$SSL_KEY_PATH" ]; then
        echo "✅ SSL certificates found, enabling HTTPS..."
        
        # SSL监听指令
        export SSL_LISTEN_DIRECTIVE="listen       443 ssl;
    listen  [::]:443 ssl;"
        
        # SSL配置块
        export SSL_CONFIG_BLOCK="http2 on;
    ssl_certificate $SSL_CERT_PATH;
    ssl_certificate_key $SSL_KEY_PATH;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 10m;
    ssl_stapling on;
    ssl_stapling_verify on;"
        
        # HTTP重定向服务器 
        export SSL_REDIRECT_SERVER="# HTTP到HTTPS重定向
server {
    listen 80;
    listen [::]:80;
    server_name $SERVER_DOMAIN;
    return 301 https://\$server_name\$request_uri;
}"
        
        # 清空内联重定向块（因为我们用独立服务器处理）
        export SSL_REDIRECT_BLOCK=""
        
        # 代理转发协议
        export PROXY_FORWARDED_PROTO="https"
        
    else
        echo "⚠️  Warning: SSL enabled but certificates not found!"
        echo "Certificate: $SSL_CERT_PATH (exists: $([ -f "$SSL_CERT_PATH" ] && echo 'yes' || echo 'no'))"
        echo "Private key: $SSL_KEY_PATH (exists: $([ -f "$SSL_KEY_PATH" ] && echo 'yes' || echo 'no'))"
        echo "Falling back to HTTP mode..."
        
        # 回退到HTTP模式
        export SSL_LISTEN_DIRECTIVE=""
        export SSL_CONFIG_BLOCK=""
        export SSL_REDIRECT_SERVER=""
        export SSL_REDIRECT_BLOCK=""
        export PROXY_FORWARDED_PROTO="http"
    fi
else
    echo "🌐 Configuring HTTP mode..."
    
    # HTTP模式配置
    export SSL_LISTEN_DIRECTIVE=""
    export SSL_CONFIG_BLOCK=""
    export SSL_REDIRECT_SERVER=""
    export SSL_REDIRECT_BLOCK=""
    export PROXY_FORWARDED_PROTO="http"
fi

echo "Configuration completed."
echo "================================"

# 处理nginx配置模板
if [ -f /etc/nginx/conf.d/default.conf.template ]; then
    echo "📄 Processing nginx configuration template..."
    envsubst '${SERVER_DOMAIN} ${SSL_LISTEN_DIRECTIVE} ${SSL_CONFIG_BLOCK} ${SSL_REDIRECT_SERVER} ${SSL_REDIRECT_BLOCK} ${PROXY_FORWARDED_PROTO} ${API_UPSTREAM} ${WEB_UPSTREAM}' \
        < /etc/nginx/conf.d/default.conf.template \
        > /etc/nginx/conf.d/default.conf
    
    echo "📋 Generated nginx configuration:"
    echo "--- /etc/nginx/conf.d/default.conf ---"
    cat /etc/nginx/conf.d/default.conf
    echo "--- End of configuration ---"
else
    echo "⚠️  Warning: Template file not found at /etc/nginx/conf.d/default.conf.template"
    echo "Using existing static configuration..."
fi

# 测试nginx配置
echo "🔍 Testing nginx configuration..."
nginx -t

# 启动nginx
echo "🚀 Starting nginx..."
exec nginx -g "daemon off;"