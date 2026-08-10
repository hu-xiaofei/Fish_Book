import { createBrowserRouter } from 'react-router-dom';
import { App } from './App';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <App />,
  },
  {
    path: '/register',
    element: <h1>注册</h1>,
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
