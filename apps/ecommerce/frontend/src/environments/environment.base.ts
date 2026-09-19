import packageInfo from '../../package.json';

export const environmentBase = {
  production: false,
  appVersion: packageInfo.version,
  apiPrefix: '/home/api',
  authUrl: 'http://localhost:8081',
  authRealm: 'ecommerce',
  clientId: 'ecommerce-app',
};
