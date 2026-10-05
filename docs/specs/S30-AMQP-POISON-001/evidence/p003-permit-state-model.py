"""Disposable P-003 state-machine comparison; stdlib-only, no repo writes."""
import base64, hashlib, hmac, json

NOW=1_000_000; KEY=b'prototype-only signing secret'
ISS='https://issuer.test'; APP_AUD='gate-api'; PERMIT_AUD='amqp-admission'
DEP='dev'; EPOCH=(7,12,43)
def b64(raw): return base64.urlsafe_b64encode(raw).rstrip(b'=').decode()
def payload(part): return json.loads(base64.urlsafe_b64decode(part+'==='))
def sign(claims):
    h=b64(b'{"alg":"HS256","typ":"JWT"}'); p=b64(json.dumps(claims,sort_keys=True,separators=(',',':')).encode()); msg=f'{h}.{p}'.encode()
    return f'{h}.{p}.{b64(hmac.new(KEY,msg,hashlib.sha256).digest())}'
def verified(claims, aud, now=NOW):
    return claims.get('iss')==ISS and claims.get('aud')==aud and claims.get('exp',0)>now and claims.get('nbf',0)<=now

def authorize(token, action, now=NOW):
    try: c=payload(token.split('.')[1])
    except Exception: return False
    if not verified(c,APP_AUD,now): return False
    if action=='PAUSE': return c.get('client')=='gate-workload' and 'gate-pause' in c.get('roles',[])
    if action=='RESUME': return c.get('sub') and c.get('client')=='operator-cli' and 'gate-resume' in c.get('roles',[])
    return False

def issue(instance, incarnation, jti, exp=NOW+5, epoch=EPOCH):
    return sign(dict(iss='gate://service',aud=PERMIT_AUD,dep=DEP,instance=instance,incarnation=incarnation,epoch=list(epoch),iat=NOW,exp=exp,jti=jti))
def signed_admit(token, identity, known_epoch, now, consumed):
    try:
        h,p,s=token.split('.'); claims=payload(p)
        if not hmac.compare_digest(s,b64(hmac.new(KEY,f'{h}.{p}'.encode(),hashlib.sha256).digest())): return False,'forged'
    except Exception: return False,'malformed'
    if claims.get('iss')!='gate://service' or claims.get('aud')!=PERMIT_AUD or claims.get('dep')!=DEP: return False,'issuer/audience/deployment'
    if claims.get('instance')!=identity[0] or claims.get('incarnation')!=identity[1]: return False,'cross-instance/incarnation'
    if tuple(claims.get('epoch',()))!=known_epoch: return False,'stale-epoch'
    if claims.get('exp',0)<=now or claims.get('exp',0)-claims.get('iat',0)>5: return False,'expiry'
    if claims.get('jti') in consumed: return False,'replay'
    consumed.add(claims['jti']); return True,'admitted'

class Online:
    def __init__(self): self.active=True; self.epoch=EPOCH; self.used=set(); self.connected=True
    def admit(self, permit, identity):
        if not self.connected: return False,'offline'
        if not self.active: return False,'paused'
        if permit[:2]!=identity: return False,'cross-instance/incarnation'
        if permit[2]!=self.epoch: return False,'stale-epoch'
        if permit in self.used: return False,'replay'
        self.used.add(permit); return True,'admitted'

checks=[]
def expect(name, actual, expected):
    assert actual==expected,(name,actual,expected); checks.append((name,actual))

# Shared authentication/authorization contract (not a difference between A/B).
workload=sign(dict(iss=ISS,aud=APP_AUD,client='gate-workload',sub='svc/orders',roles=['gate-pause'],nbf=NOW-1,exp=NOW+60))
operator=sign(dict(iss=ISS,aud=APP_AUD,client='operator-cli',sub='user/alice',roles=['gate-resume'],nbf=NOW-1,exp=NOW+60))
public=sign(dict(iss=ISS,aud=APP_AUD,client='ecommerce-app',sub='public',roles=['ORDER_WRITE'],nbf=NOW-1,exp=NOW+60))
expect('workload PAUSE only', (authorize(workload,'PAUSE'),authorize(workload,'RESUME')), (True,False))
expect('individual operator RESUME only', (authorize(operator,'PAUSE'),authorize(operator,'RESUME')), (False,True))
expect('public ecommerce identity denied', (authorize(public,'PAUSE'),authorize(public,'RESUME')), (False,False))
expect('wrong audience denied', authorize(sign(dict(iss=ISS,aud='wrong',client='operator-cli',sub='alice',roles=['gate-resume'],nbf=0,exp=NOW+20)),'RESUME'), False)
expect('expired operator denied', authorize(sign(dict(iss=ISS,aud=APP_AUD,client='operator-cli',sub='alice',roles=['gate-resume'],nbf=0,exp=NOW)),'RESUME'), False)
# Deliberate counterexample: authorize() decodes claims but does not verify the
# identity-token signature. An attacker can replace the payload and keep the
# original invalid signature; this helper still authorizes the forged role.
original_header, _, original_signature = public.split('.')
forged_claims = dict(iss=ISS, aud=APP_AUD, client='operator-cli', sub='forged', roles=['gate-resume'], nbf=NOW-1, exp=NOW+60)
forged_payload = b64(json.dumps(forged_claims,sort_keys=True,separators=(',',':')).encode())
forged_identity = f'{original_header}.{forged_payload}.{original_signature}'
expect('known-bad identity helper accepts payload with invalid signature', authorize(forged_identity,'RESUME'), True)

alice=('app-1','boot-A'); bob=('app-2','boot-B'); consumed=set(); good=issue(*alice,'n1')
expect('signed valid',signed_admit(good,alice,EPOCH,NOW,consumed),(True,'admitted'))
expect('signed replay same process',signed_admit(good,alice,EPOCH,NOW,consumed),(False,'replay'))
expect('signed cross-instance',signed_admit(issue(*alice,'n2'),bob,EPOCH,NOW,set()),(False,'cross-instance/incarnation'))
expect('signed stale generation',signed_admit(issue(*alice,'n3',epoch=(7,12,42)),alice,EPOCH,NOW,set()),(False,'stale-epoch'))
forged=good[:-1]+('A' if good[-1]!='A' else 'B')
expect('signed tampering',signed_admit(forged,alice,EPOCH,NOW,set()),(False,'forged'))
expect('signed permit expired by five seconds',signed_admit(issue(*alice,'n4'),alice,EPOCH,NOW+5,set()),(False,'expiry'))
# A permit minted before PAUSE remains usable by an isolated app until its 5s expiry.
expect('signed still-valid permit after unseen PAUSE',signed_admit(issue(*alice,'n5'),alice,EPOCH,NOW+1,set()),(True,'admitted'))
expect('signed no starts after expiry',signed_admit(issue(*alice,'n6'),alice,EPOCH,NOW+5,set()),(False,'expiry'))
expect('signed token rejected after restart incarnation',signed_admit(issue(*alice,'n7'),('app-1','boot-B'),EPOCH,NOW,set()),(False,'cross-instance/incarnation'))

gate=Online(); p=(*alice,EPOCH,'opaque-1')
expect('opaque valid online',gate.admit(p,alice),(True,'admitted'))
expect('opaque replay',gate.admit(p,alice),(False,'replay'))
expect('opaque cross-instance',gate.admit((*alice,EPOCH,'opaque-2'),bob),(False,'cross-instance/incarnation'))
expect('opaque stale generation',gate.admit((*alice,(7,12,42),'opaque-3'),alice),(False,'stale-epoch'))
gate.connected=False
expect('opaque gate loss fails closed immediately',gate.admit((*alice,EPOCH,'opaque-4'),alice),(False,'offline'))
print(f'{len(checks)} deterministic assertions passed')
for name,result in checks: print(f'{name}: {result}')
print('Model uses HMAC only to exercise token parsing/tamper rejection; this does not prove key custody, asymmetric signature security, OIDC verification, distributed replay protection, timing, or runtime races.')
