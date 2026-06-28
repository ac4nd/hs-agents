import { useMagic } from 'react-magic';
function C() {
    const m = useMagic();  // 未注册
    return <div>{m}</div>;
}
