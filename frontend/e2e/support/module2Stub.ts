import { createServer, type Server } from 'node:http';

const PORT = 8082;

/**
 * Módulo 2 simulado para los flujos que consultan reservas (programar un bloqueo técnico y dar de
 * baja una habitación): `GET /api/reservations` responde que no hay reservas en conflicto. Escucha
 * en el puerto que el backend usa en perfil `dev` (`hospitua.module2.base-url`). Si el puerto ya
 * está ocupado (otro simulador o el Módulo 2 real), no hace nada y se usa el que esté.
 */
export function startModule2Stub(): Promise<Server | null> {
  return new Promise((resolve, reject) => {
    const server = createServer((request, response) => {
      if (request.method === 'GET' && request.url?.startsWith('/api/reservations')) {
        response.writeHead(200, { 'Content-Type': 'application/json' });
        response.end(JSON.stringify({ items: [] }));
        return;
      }
      response.writeHead(404).end();
    });
    server.once('error', (error: NodeJS.ErrnoException) => {
      if (error.code === 'EADDRINUSE') {
        resolve(null);
      } else {
        reject(error);
      }
    });
    server.listen(PORT, () => resolve(server));
  });
}
