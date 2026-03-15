import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { KeyRound, LogIn, UserPlus, RefreshCw } from 'lucide-react';
import { Card } from '../components/ui/card';
import { Label } from '../components/ui/label';
import { Input } from '../components/ui/input';
import { Button } from '../components/ui/button';
import { useToast } from '../components/ui/toast';
import { useAuth } from '../auth/AuthContext';
import { cn } from '../lib/utils';

type Mode = 'login' | 'register';

export default function LoginPage() {
    const toast = useToast();
    const navigate = useNavigate();
    const location = useLocation();
    const { login, register, isAuthenticated, user, loading } = useAuth();

    const [mode, setMode] = useState<Mode>('login');

    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [submitting, setSubmitting] = useState(false);

    const [registerName, setRegisterName] = useState('');
    const [registerUsername, setRegisterUsername] = useState('');
    const [registerPassword, setRegisterPassword] = useState('');

    useEffect(() => {
        if (!loading && isAuthenticated) {
            if (user?.admin && user.requiresInitialSetup) {
                navigate('/onboarding', { replace: true });
            } else {
                navigate('/projekte', { replace: true });
            }
        }
    }, [isAuthenticated, loading, navigate, user]);

    const handleLogin = async (event: FormEvent) => {
        event.preventDefault();
        setSubmitting(true);

        const result = await login(username, password);
        setSubmitting(false);

        if (!result.success) {
            toast.error(result.message || 'Login fehlgeschlagen.');
            return;
        }

        const fromPath = (location.state as { from?: string } | null)?.from;
        if (fromPath && fromPath !== '/login') {
            navigate(fromPath, { replace: true });
            return;
        }

        navigate('/projekte', { replace: true });
    };

    const handleRegister = async (event: FormEvent) => {
        event.preventDefault();

        if (registerPassword.length < 8) {
            toast.error('Passwort muss mindestens 8 Zeichen haben.');
            return;
        }

        setSubmitting(true);
        const result = await register(registerName, registerUsername, registerPassword);
        setSubmitting(false);

        if (!result.success) {
            toast.error(result.message || 'Registrierung fehlgeschlagen.');
            return;
        }

        toast.success('Benutzer angelegt. Bitte jetzt einloggen.');
        setMode('login');
        setUsername(registerUsername.trim());
        setPassword('');
        setRegisterName('');
        setRegisterUsername('');
        setRegisterPassword('');
    };

    return (
        <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
            <div className="w-full max-w-md space-y-4">
                <div className="text-center">
                    <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">ERP Handwerk</p>
                    <h1 className="text-3xl font-bold text-slate-900">ANMELDUNG</h1>
                    <p className="text-slate-500 mt-1">Bitte melden Sie sich mit Ihrem Frontend-Benutzer an.</p>
                </div>

                <Card className="p-6 border-rose-100 shadow-lg">
                    <div className="flex gap-1 bg-slate-100 p-1 rounded-lg mb-5">
                        <button
                            onClick={() => setMode('login')}
                            className={cn(
                                'flex-1 px-3 py-2 rounded-md text-sm font-medium transition',
                                mode === 'login' ? 'bg-white text-rose-700 shadow-sm' : 'text-slate-600 hover:text-slate-900'
                            )}
                        >
                            <LogIn className="w-4 h-4 inline-block mr-2" />
                            Login
                        </button>
                        <button
                            onClick={() => setMode('register')}
                            className={cn(
                                'flex-1 px-3 py-2 rounded-md text-sm font-medium transition',
                                mode === 'register' ? 'bg-white text-rose-700 shadow-sm' : 'text-slate-600 hover:text-slate-900'
                            )}
                        >
                            <UserPlus className="w-4 h-4 inline-block mr-2" />
                            Konto anlegen
                        </button>
                    </div>

                    {mode === 'login' ? (
                        <form className="space-y-4" onSubmit={handleLogin}>
                            <div>
                                <Label>Benutzername</Label>
                                <Input
                                    value={username}
                                    onChange={(e) => setUsername(e.target.value)}
                                    placeholder="z.B. max.mustermann"
                                    autoComplete="username"
                                />
                            </div>
                            <div>
                                <Label>Passwort</Label>
                                <Input
                                    type="password"
                                    value={password}
                                    onChange={(e) => setPassword(e.target.value)}
                                    autoComplete="current-password"
                                />
                            </div>
                            <Button
                                type="submit"
                                className="w-full bg-rose-600 text-white hover:bg-rose-700"
                                disabled={submitting || !username.trim() || !password}
                            >
                                {submitting ? <RefreshCw className="w-4 h-4 mr-2 animate-spin" /> : <KeyRound className="w-4 h-4 mr-2" />}
                                Anmelden
                            </Button>
                        </form>
                    ) : (
                        <form className="space-y-4" onSubmit={handleRegister}>
                            <div>
                                <Label>Anzeigename</Label>
                                <Input
                                    value={registerName}
                                    onChange={(e) => setRegisterName(e.target.value)}
                                    placeholder="z.B. Max Mustermann"
                                />
                            </div>
                            <div>
                                <Label>Benutzername</Label>
                                <Input
                                    value={registerUsername}
                                    onChange={(e) => setRegisterUsername(e.target.value)}
                                    placeholder="z.B. max.mustermann"
                                    autoComplete="username"
                                />
                            </div>
                            <div>
                                <Label>Passwort (mind. 8 Zeichen)</Label>
                                <Input
                                    type="password"
                                    value={registerPassword}
                                    onChange={(e) => setRegisterPassword(e.target.value)}
                                    autoComplete="new-password"
                                />
                            </div>
                            <Button
                                type="submit"
                                className="w-full bg-rose-600 text-white hover:bg-rose-700"
                                disabled={
                                    submitting
                                    || !registerName.trim()
                                    || !registerUsername.trim()
                                    || registerPassword.length < 8
                                }
                            >
                                {submitting ? <RefreshCw className="w-4 h-4 mr-2 animate-spin" /> : <UserPlus className="w-4 h-4 mr-2" />}
                                Konto erstellen
                            </Button>
                        </form>
                    )}
                </Card>
            </div>
        </div>
    );
}
