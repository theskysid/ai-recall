# Recall — frontend

React 19 + Vite dev server for [Recall](../README.md). Normally run via
`docker compose -f docker-compose.local.yml up`; to run it on its own:

```bash
npm install
npm run dev      # dev server on 5173
npm run build    # production build
npm run lint     # ESLint
```

`VITE_API_URL` points at the backend (`http://localhost:8080` locally) and is
read at build time — see `.env.example` in the project root.
