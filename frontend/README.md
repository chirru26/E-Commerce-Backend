# ShopLite Frontend

Basic vanilla HTML/CSS/JavaScript frontend for the E-Commerce Backend.

## Run

Start the backend and gateway:

```bash
docker compose up -d
```

The browser API base is `http://localhost:8080`.

Serve this directory with a static HTTP server:

```bash
cd frontend
python -m http.server 5500
```

Open `http://localhost:5500`.

The UI currently uses the public catalog endpoints plus register/login/refresh/logout and the authenticated cart endpoints. JWT access and refresh tokens are stored in browser localStorage for this development/demo frontend.