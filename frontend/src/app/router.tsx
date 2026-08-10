import { createBrowserRouter } from 'react-router-dom';
import { RegisterPage } from '../features/auth/pages/RegisterPage';
import { App } from './App';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <App />,
  },
  {
    path: '/register',
    element: <RegisterPage />,
  },
  {
    path: '/login',
    element: <h1>登录</h1>,
  },
  {
    path: '/profile',
    element: <h1>个人资料</h1>,
  },
]);
