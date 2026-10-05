"""Disposable P-003 state-machine comparison; stdlib-only, no repo writes."""
import base64, hashlib, hmac, json
from concurrent.futures import ThreadPoolExecutor
from threading import Barrier, Lock

NOW=1_000_000; KEY=b'prototype-only signing secret'
ISS='https://issuer.test'; APP_AUD='gate-api'; PERMIT_AUD='amqp-admission'
DEP='dev'; EPOCH=(7,12,43)
def b64(raw): return base64.urlsafe_b64encode(raw).rstrip(b'=').decode()
def payload(part): return json.loads(base64.urlsafe_b64decode(part+'==='))
def sign(claims, alg='HS256'):
    h=b64(json.dumps({'alg':alg,'typ':'JWT'},sort_keys=True,separators=(',',':')).encode()); p=b64(json.dumps(claims,sort_keys=True,separators=(',',':')).encode()); msg=f'{h}.{p}'.encode()
    return f'{h}.{p}.{b64(hmac.new(KEY,msg,hashlib.sha256).digest())}'
def verify_token(token):
    try:
        h,p,s=token.split('.')
        header=json.loads(base64.urlsafe_b64decode(h+'==='))
        if header!={'alg':'HS256','typ':'JWT'}: return None
        expected=b64(hmac.new(KEY,f'{h}.{p}'.encode(),hashlib.sha256).digest())
        if not hmac.compare_digest(s,expected): return None
        claims=payload(p)
        return claims if isinstance(claims,dict) else None
    except Exception:
        return None
def verified(claims, aud, now=NOW):
    return claims.get('iss')==ISS and claims.get('aud')==aud and claims.get('exp',0)>now and claims.get('nbf',0)<=now

def authorize(token, action, now=NOW):
    c=verify_token(token)
    if c is None: return False
    if not verified(c,APP_AUD,now): return False
    if action=='PAUSE': return c.get('client')=='gate-workload' and 'gate-pause' in c.get('roles',[])
    if action=='RESUME': return c.get('sub') and c.get('client')=='operator-cli' and 'gate-resume' in c.get('roles',[])
    return False

def issue(instance, incarnation, jti, exp=NOW+5, epoch=EPOCH, subject='svc/orders', nonce=None, iat=NOW, nbf=NOW):
    return sign(dict(iss='gate://service',aud=PERMIT_AUD,dep=DEP,instance=instance,incarnation=incarnation,sub=subject,nonce=nonce or f'nonce-{jti}',epoch=list(epoch),iat=iat,nbf=nbf,exp=exp,jti=jti))

class ReplayState:
    def __init__(self): self.used=set(); self.lock=Lock()
    def consume_once(self,jti):
        with self.lock:
            if jti in self.used: return False
            self.used.add(jti); return True

def signed_admit(token, identity, known_epoch, now, consumed, expected_nonce):
    try:
        h,p,s=token.split('.'); header=json.loads(base64.urlsafe_b64decode(h+'===')); claims=payload(p)
        if header!={'alg':'HS256','typ':'JWT'}: return False,'algorithm'
        if not hmac.compare_digest(s,b64(hmac.new(KEY,f'{h}.{p}'.encode(),hashlib.sha256).digest())): return False,'forged'
    except Exception: return False,'malformed'
    if not isinstance(claims,dict): return False,'malformed-claims'
    if claims.get('iss')!='gate://service' or claims.get('aud')!=PERMIT_AUD or claims.get('dep')!=DEP: return False,'issuer/audience/deployment'
    if claims.get('instance')!=identity[0] or claims.get('incarnation')!=identity[1] or claims.get('sub')!=identity[2]: return False,'cross-instance/incarnation/subject'
    if claims.get('nonce')!=expected_nonce: return False,'nonce'
    if tuple(claims.get('epoch',()))!=known_epoch: return False,'stale-epoch'
    if any(type(claims.get(name)) is not int for name in ('iat','nbf','exp')): return False,'malformed-time'
    if not isinstance(claims.get('jti'),str) or not claims['jti']: return False,'malformed-jti'
    issued=claims.get('iat',0); not_before=claims.get('nbf',0); expires=claims.get('exp',0)
    if issued>now or not_before>now: return False,'not-yet-valid'
    if expires<=issued or expires-issued>5: return False,'lifetime'
    if expires<=now: return False,'expiry'
    if not consumed.consume_once(claims['jti']): return False,'replay'
    return True,'admitted'

class Online:
    def __init__(self): self.active=True; self.epoch=EPOCH; self.used=set(); self.lock=Lock(); self.connected=True
    def admit(self, permit, identity):
        with self.lock:
            if not self.connected: return False,'offline'
            if not self.active: return False,'paused'
            if permit[:3]!=identity: return False,'cross-instance/incarnation/subject'
            if permit[3]!=self.epoch: return False,'stale-epoch'
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
# A payload replacement that preserves the original signature must be denied.
original_header, _, original_signature = public.split('.')
forged_claims = dict(iss=ISS, aud=APP_AUD, client='operator-cli', sub='forged', roles=['gate-resume'], nbf=NOW-1, exp=NOW+60)
forged_payload = b64(json.dumps(forged_claims,sort_keys=True,separators=(',',':')).encode())
forged_identity = f'{original_header}.{forged_payload}.{original_signature}'
expect('identity payload tampering rejected by signature verification', authorize(forged_identity,'RESUME'), False)
expect('unsupported identity algorithm denied', authorize(sign(dict(iss=ISS,aud=APP_AUD,client='operator-cli',sub='alice',roles=['gate-resume'],nbf=0,exp=NOW+20),alg='HS384'),'RESUME'), False)
expect('signed identity payload array denied',authorize(sign([1,2,3]),'RESUME'),False)

alice=('app-1','boot-A','svc/orders'); bob=('app-2','boot-B','svc/shipments'); consumed=ReplayState(); good=issue(alice[0],alice[1],'n1',subject=alice[2])
expect('signed valid',signed_admit(good,alice,EPOCH,NOW,consumed,'nonce-n1'),(True,'admitted'))
expect('signed replay same process',signed_admit(good,alice,EPOCH,NOW,consumed,'nonce-n1'),(False,'replay'))
expect('signed cross-instance',signed_admit(issue(alice[0],alice[1],'n2',subject=alice[2]),bob,EPOCH,NOW,ReplayState(),'nonce-n2'),(False,'cross-instance/incarnation/subject'))
expect('signed stale generation',signed_admit(issue(alice[0],alice[1],'n3',epoch=(7,12,42),subject=alice[2]),alice,EPOCH,NOW,ReplayState(),'nonce-n3'),(False,'stale-epoch'))
expect('signed wrong nonce',signed_admit(issue(alice[0],alice[1],'n8',subject=alice[2]),alice,EPOCH,NOW,ReplayState(),'unexpected'),(False,'nonce'))
forged=good[:-1]+('A' if good[-1]!='A' else 'B')
expect('signed tampering',signed_admit(forged,alice,EPOCH,NOW,ReplayState(),'nonce-n1'),(False,'forged'))
expect('signed unsupported algorithm',signed_admit(sign({'iss':'gate://service','aud':PERMIT_AUD,'dep':DEP,'instance':alice[0],'incarnation':alice[1],'sub':alice[2],'nonce':'nonce-bad-alg','epoch':list(EPOCH),'iat':NOW,'nbf':NOW,'exp':NOW+5,'jti':'bad-alg'},alg='HS384'),alice,EPOCH,NOW,ReplayState(),'nonce-bad-alg'),(False,'algorithm'))
expect('signed permit expired by five seconds',signed_admit(issue(alice[0],alice[1],'n4',subject=alice[2]),alice,EPOCH,NOW+5,ReplayState(),'nonce-n4'),(False,'expiry'))
expect('signed future iat denied',signed_admit(issue(alice[0],alice[1],'n9',subject=alice[2],iat=NOW+20,nbf=NOW+20,exp=NOW+22),alice,EPOCH,NOW,ReplayState(),'nonce-n9'),(False,'not-yet-valid'))
expect('signed future nbf denied',signed_admit(issue(alice[0],alice[1],'n10',subject=alice[2],nbf=NOW+1),alice,EPOCH,NOW,ReplayState(),'nonce-n10'),(False,'not-yet-valid'))
expect('signed nonpositive lifetime denied',signed_admit(issue(alice[0],alice[1],'n11',subject=alice[2],exp=NOW),alice,EPOCH,NOW,ReplayState(),'nonce-n11'),(False,'lifetime'))
expect('signed lifetime above five seconds denied',signed_admit(issue(alice[0],alice[1],'n12',subject=alice[2],exp=NOW+6),alice,EPOCH,NOW,ReplayState(),'nonce-n12'),(False,'lifetime'))
expect('signed missing nbf rejected',signed_admit(sign({'iss':'gate://service','aud':PERMIT_AUD,'dep':DEP,'instance':alice[0],'incarnation':alice[1],'sub':alice[2],'nonce':'nonce-missing-nbf','epoch':list(EPOCH),'iat':NOW,'exp':NOW+5,'jti':'missing-nbf'}),alice,EPOCH,NOW,ReplayState(),'nonce-missing-nbf'),(False,'malformed-time'))
expect('signed malformed exp rejected',signed_admit(sign({'iss':'gate://service','aud':PERMIT_AUD,'dep':DEP,'instance':alice[0],'incarnation':alice[1],'sub':alice[2],'nonce':'nonce-bad-exp','epoch':list(EPOCH),'iat':NOW,'nbf':NOW,'exp':'soon','jti':'bad-exp'}),alice,EPOCH,NOW,ReplayState(),'nonce-bad-exp'),(False,'malformed-time'))
expect('signed missing jti rejected',signed_admit(sign({'iss':'gate://service','aud':PERMIT_AUD,'dep':DEP,'instance':alice[0],'incarnation':alice[1],'sub':alice[2],'nonce':'nonce-missing-jti','epoch':list(EPOCH),'iat':NOW,'nbf':NOW,'exp':NOW+5}),alice,EPOCH,NOW,ReplayState(),'nonce-missing-jti'),(False,'malformed-jti'))
expect('signed permit payload array denied',signed_admit(sign([1,2,3]),alice,EPOCH,NOW,ReplayState(),'anything'),(False,'malformed-claims'))
race_token=issue(alice[0],alice[1],'race',subject=alice[2]); race_state=ReplayState(); race_barrier=Barrier(8)
def concurrent_start(_):
    race_barrier.wait()
    return signed_admit(race_token,alice,EPOCH,NOW,race_state,'nonce-race')
with ThreadPoolExecutor(max_workers=8) as pool:
    race_results=list(pool.map(concurrent_start,range(8)))
expect('concurrent duplicate starts admit exactly once',sum(result[0] for result in race_results),1)
# A permit minted before PAUSE remains usable by an isolated app until its 5s expiry.
expect('signed still-valid permit after unseen PAUSE',signed_admit(issue(alice[0],alice[1],'n5',subject=alice[2]),alice,EPOCH,NOW+1,ReplayState(),'nonce-n5'),(True,'admitted'))
expect('signed no starts after expiry',signed_admit(issue(alice[0],alice[1],'n6',subject=alice[2]),alice,EPOCH,NOW+5,ReplayState(),'nonce-n6'),(False,'expiry'))
expect('signed token rejected after restart incarnation',signed_admit(issue(alice[0],alice[1],'n7',subject=alice[2]),('app-1','boot-B',alice[2]),EPOCH,NOW,ReplayState(),'nonce-n7'),(False,'cross-instance/incarnation/subject'))

gate=Online(); p=(*alice,EPOCH,'opaque-1')
expect('opaque valid online',gate.admit(p,alice),(True,'admitted'))
expect('opaque replay',gate.admit(p,alice),(False,'replay'))
expect('opaque cross-instance',gate.admit((*alice,EPOCH,'opaque-2'),bob),(False,'cross-instance/incarnation/subject'))
expect('opaque stale generation',gate.admit((*alice,(7,12,42),'opaque-3'),alice),(False,'stale-epoch'))
gate.connected=False
expect('opaque gate loss fails closed immediately',gate.admit((*alice,EPOCH,'opaque-4'),alice),(False,'offline'))
print(f'{len(checks)} deterministic assertions passed')
for name,result in checks: print(f'{name}: {result}')
print('Model uses HMAC only to exercise token parsing/tamper rejection; this does not prove key custody, asymmetric signature security, OIDC verification, distributed replay protection, timing, or runtime races.')
