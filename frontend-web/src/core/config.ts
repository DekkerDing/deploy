export const config = {
  get apiBaseUrl(): string {
    if (typeof window !== 'undefined') {
      const { protocol, hostname, port } = window.location;
      return `${protocol}//${hostname}${port ? ':' + port : ''}`;
    }
    return 'http://localhost:8080';
  },
};