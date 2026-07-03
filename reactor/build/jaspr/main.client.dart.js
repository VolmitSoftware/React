(function dartProgram(){function copyProperties(a,b){var s=Object.keys(a)
for(var r=0;r<s.length;r++){var q=s[r]
b[q]=a[q]}}function mixinPropertiesHard(a,b){var s=Object.keys(a)
for(var r=0;r<s.length;r++){var q=s[r]
if(!b.hasOwnProperty(q)){b[q]=a[q]}}}function mixinPropertiesEasy(a,b){Object.assign(b,a)}var z=function(){var s=function(){}
s.prototype={p:{}}
var r=new s()
if(!(Object.getPrototypeOf(r)&&Object.getPrototypeOf(r).p===s.prototype.p))return false
try{if(typeof navigator!="undefined"&&typeof navigator.userAgent=="string"&&navigator.userAgent.indexOf("Chrome/")>=0)return true
if(typeof version=="function"&&version.length==0){var q=version()
if(/^\d+\.\d+\.\d+\.\d+$/.test(q))return true}}catch(p){}return false}()
function inherit(a,b){a.prototype.constructor=a
a.prototype["$i"+a.name]=a
if(b!=null){if(z){Object.setPrototypeOf(a.prototype,b.prototype)
return}var s=Object.create(b.prototype)
copyProperties(a.prototype,s)
a.prototype=s}}function inheritMany(a,b){for(var s=0;s<b.length;s++){inherit(b[s],a)}}function mixinEasy(a,b){mixinPropertiesEasy(b.prototype,a.prototype)
a.prototype.constructor=a}function mixinHard(a,b){mixinPropertiesHard(b.prototype,a.prototype)
a.prototype.constructor=a}function lazy(a,b,c,d){var s=a
a[b]=s
a[c]=function(){if(a[b]===s){a[b]=d()}a[c]=function(){return this[b]}
return a[b]}}function lazyFinal(a,b,c,d){var s=a
a[b]=s
a[c]=function(){if(a[b]===s){var r=d()
if(a[b]!==s){A.Ik(b)}a[b]=r}var q=a[b]
a[c]=function(){return q}
return q}}function makeConstList(a,b){if(b!=null)A.a(a,b)
a.$flags=7
return a}function convertToFastObject(a){function t(){}t.prototype=a
new t()
return a}function convertAllToFastObject(a){for(var s=0;s<a.length;++s){convertToFastObject(a[s])}}var y=0
function instanceTearOffGetter(a,b){var s=null
return a?function(c){if(s===null)s=A.zg(b)
return new s(c,this)}:function(){if(s===null)s=A.zg(b)
return new s(this,null)}}function staticTearOffGetter(a){var s=null
return function(){if(s===null)s=A.zg(a).prototype
return s}}var x=0
function tearOffParameters(a,b,c,d,e,f,g,h,i,j){if(typeof h=="number"){h+=x}return{co:a,iS:b,iI:c,rC:d,dV:e,cs:f,fs:g,fT:h,aI:i||0,nDA:j}}function installStaticTearOff(a,b,c,d,e,f,g,h){var s=tearOffParameters(a,true,false,c,d,e,f,g,h,false)
var r=staticTearOffGetter(s)
a[b]=r}function installInstanceTearOff(a,b,c,d,e,f,g,h,i,j){c=!!c
var s=tearOffParameters(a,false,c,d,e,f,g,h,i,!!j)
var r=instanceTearOffGetter(c,s)
a[b]=r}function setOrUpdateInterceptorsByTag(a){var s=v.interceptorsByTag
if(!s){v.interceptorsByTag=a
return}copyProperties(a,s)}function setOrUpdateLeafTags(a){var s=v.leafTags
if(!s){v.leafTags=a
return}copyProperties(a,s)}function updateTypes(a){var s=v.types
var r=s.length
s.push.apply(s,a)
return r}function updateHolder(a,b){copyProperties(b,a)
return a}var hunkHelpers=function(){var s=function(a,b,c,d,e){return function(f,g,h,i){return installInstanceTearOff(f,g,a,b,c,d,[h],i,e,false)}},r=function(a,b,c,d){return function(e,f,g,h){return installStaticTearOff(e,f,a,b,c,[g],h,d)}}
return{inherit:inherit,inheritMany:inheritMany,mixin:mixinEasy,mixinHard:mixinHard,installStaticTearOff:installStaticTearOff,installInstanceTearOff:installInstanceTearOff,_instance_0u:s(0,0,null,["$0"],0),_instance_1u:s(0,1,null,["$1"],0),_instance_2u:s(0,2,null,["$2"],0),_instance_0i:s(1,0,null,["$0"],0),_instance_1i:s(1,1,null,["$1"],0),_instance_2i:s(1,2,null,["$2"],0),_static_0:r(0,null,["$0"],0),_static_1:r(1,null,["$1"],0),_static_2:r(2,null,["$2"],0),makeConstList:makeConstList,lazy:lazy,lazyFinal:lazyFinal,updateHolder:updateHolder,convertToFastObject:convertToFastObject,updateTypes:updateTypes,setOrUpdateInterceptorsByTag:setOrUpdateInterceptorsByTag,setOrUpdateLeafTags:setOrUpdateLeafTags}}()
function initializeDeferredHunk(a){x=v.types.length
a(hunkHelpers,v,w,$)}var J={
zp(a,b,c,d){return{i:a,p:b,e:c,x:d}},
zi(a){var s,r,q,p,o,n=a[v.dispatchPropertyName]
if(n==null)if($.zl==null){A.HW()
n=a[v.dispatchPropertyName]}if(n!=null){s=n.p
if(!1===s)return n.i
if(!0===s)return a
r=Object.getPrototypeOf(a)
if(s===r)return n.i
if(n.e===r)throw A.d(A.yW("Return interceptor for "+A.w(s(a,n))))}q=a.constructor
if(q==null)p=null
else{o=$.vn
if(o==null)o=$.vn=v.getIsolateTag("_$dart_js")
p=q[o]}if(p!=null)return p
p=A.I2(a)
if(p!=null)return p
if(typeof a=="function")return B.d7
s=Object.getPrototypeOf(a)
if(s==null)return B.bi
if(s===Object.prototype)return B.bi
if(typeof q=="function"){o=$.vn
if(o==null)o=$.vn=v.getIsolateTag("_$dart_js")
Object.defineProperty(q,o,{value:B.ao,enumerable:false,writable:true,configurable:true})
return B.ao}return B.ao},
yJ(a,b){if(a<0||a>4294967295)throw A.d(A.an(a,0,4294967295,"length",null))
return J.Eh(new Array(a),b)},
Eg(a,b){if(a<0)throw A.d(A.ai("Length must be a non-negative integer: "+a,null))
return A.a(new Array(a),b.h("D<0>"))},
Au(a,b){if(a<0)throw A.d(A.ai("Length must be a non-negative integer: "+a,null))
return A.a(new Array(a),b.h("D<0>"))},
Eh(a,b){var s=A.a(a,b.h("D<0>"))
s.$flags=1
return s},
Ei(a,b){var s=t.bP
return J.yn(s.a(a),s.a(b))},
Av(a){if(a<256)switch(a){case 9:case 10:case 11:case 12:case 13:case 32:case 133:case 160:return!0
default:return!1}switch(a){case 5760:case 8192:case 8193:case 8194:case 8195:case 8196:case 8197:case 8198:case 8199:case 8200:case 8201:case 8202:case 8232:case 8233:case 8239:case 8287:case 12288:case 65279:return!0
default:return!1}},
Ej(a,b){var s,r
for(s=a.length;b<s;){r=a.charCodeAt(b)
if(r!==32&&r!==13&&!J.Av(r))break;++b}return b},
Ek(a,b){var s,r,q
for(s=a.length;b>0;b=r){r=b-1
if(!(r<s))return A.f(a,r)
q=a.charCodeAt(r)
if(q!==32&&q!==13&&!J.Av(q))break}return b},
em(a){if(typeof a=="number"){if(Math.floor(a)==a)return J.hn.prototype
return J.k9.prototype}if(typeof a=="string")return J.dj.prototype
if(a==null)return J.ho.prototype
if(typeof a=="boolean")return J.hm.prototype
if(Array.isArray(a))return J.D.prototype
if(typeof a!="object"){if(typeof a=="function")return J.dk.prototype
if(typeof a=="symbol")return J.hr.prototype
if(typeof a=="bigint")return J.hp.prototype
return a}if(a instanceof A.u)return a
return J.zi(a)},
aT(a){if(typeof a=="string")return J.dj.prototype
if(a==null)return a
if(Array.isArray(a))return J.D.prototype
if(typeof a!="object"){if(typeof a=="function")return J.dk.prototype
if(typeof a=="symbol")return J.hr.prototype
if(typeof a=="bigint")return J.hp.prototype
return a}if(a instanceof A.u)return a
return J.zi(a)},
bl(a){if(a==null)return a
if(Array.isArray(a))return J.D.prototype
if(typeof a!="object"){if(typeof a=="function")return J.dk.prototype
if(typeof a=="symbol")return J.hr.prototype
if(typeof a=="bigint")return J.hp.prototype
return a}if(a instanceof A.u)return a
return J.zi(a)},
Hp(a){if(typeof a=="number")return J.eQ.prototype
if(typeof a=="string")return J.dj.prototype
if(a==null)return a
if(!(a instanceof A.u))return J.e7.prototype
return a},
Cz(a){if(typeof a=="string")return J.dj.prototype
if(a==null)return a
if(!(a instanceof A.u))return J.e7.prototype
return a},
a8(a,b){if(a==null)return b==null
if(typeof a!="object")return b!=null&&a===b
return J.em(a).N(a,b)},
be(a,b){if(typeof b==="number")if(Array.isArray(a)||typeof a=="string"||A.I1(a,a[v.dispatchPropertyName]))if(b>>>0===b&&b<a.length)return a[b]
return J.aT(a).j(a,b)},
d9(a,b,c){return J.bl(a).i(a,b,c)},
fJ(a,b){return J.bl(a).m(a,b)},
zA(a,b){return J.Cz(a).bF(a,b)},
Dl(a,b){return J.bl(a).cc(a,b)},
yn(a,b){return J.Hp(a).P(a,b)},
zB(a,b){return J.aT(a).v(a,b)},
nr(a,b){return J.bl(a).X(a,b)},
Z(a){return J.em(a).gI(a)},
eq(a){return J.aT(a).gL(a)},
zC(a){return J.aT(a).ga1(a)},
aE(a){return J.bl(a).gC(a)},
b4(a){return J.aT(a).gn(a)},
yo(a){return J.em(a).ga2(a)},
cq(a,b){return J.bl(a).bM(a,b)},
aU(a,b,c){return J.bl(a).aZ(a,b,c)},
Dm(a,b,c,d){return J.bl(a).bs(a,b,c,d)},
Dn(a,b,c){return J.Cz(a).bt(a,b,c)},
Do(a,b){return J.aT(a).sn(a,b)},
ns(a,b){return J.bl(a).aC(a,b)},
zD(a,b){return J.bl(a).ai(a,b)},
Dp(a){return J.bl(a).dG(a)},
aF(a){return J.em(a).k(a)},
zE(a,b){return J.bl(a).dJ(a,b)},
k6:function k6(){},
hm:function hm(){},
ho:function ho(){},
hq:function hq(){},
dl:function dl(){},
kC:function kC(){},
e7:function e7(){},
dk:function dk(){},
hp:function hp(){},
hr:function hr(){},
D:function D(a){this.$ti=a},
k8:function k8(){},
q_:function q_(a){this.$ti=a},
dI:function dI(a,b,c){var _=this
_.a=a
_.b=b
_.c=0
_.d=null
_.$ti=c},
eQ:function eQ(){},
hn:function hn(){},
k9:function k9(){},
dj:function dj(){}},A={yL:function yL(){},
Ah(a,b,c){if(t.gt.b(a))return new A.ia(a,b.h("@<0>").A(c).h("ia<1,2>"))
return new A.dK(a,b.h("@<0>").A(c).h("dK<1,2>"))},
Az(a){return new A.cG("Field '"+a+"' has been assigned during initialization.")},
Em(a){return new A.cG("Field '"+a+"' has not been initialized.")},
En(a){return new A.cG("Local '"+a+"' has not been initialized.")},
El(a){return new A.cG("Field '"+a+"' has already been initialized.")},
y1(a){var s,r=a^48
if(r<=9)return r
s=a|32
if(97<=s&&s<=102)return s-87
return-1},
V(a,b){a=a+b&536870911
a=a+((a&524287)<<10)&536870911
return a^a>>>6},
cV(a){a=a+((a&67108863)<<3)&536870911
a^=a>>>11
return a+((a&16383)<<15)&536870911},
fF(a,b,c){return a},
zn(a){var s,r
for(s=$.bG.length,r=0;r<s;++r)if(a===$.bG[r])return!0
return!1},
e4(a,b,c,d){A.bo(b,"start")
if(c!=null){A.bo(c,"end")
if(b>c)A.ak(A.an(b,0,c,"start",null))}return new A.e3(a,b,c,d.h("e3<0>"))},
qs(a,b,c,d){if(t.gt.b(a))return new A.dL(a,b,c.h("@<0>").A(d).h("dL<1,2>"))
return new A.bi(a,b,c.h("@<0>").A(d).h("bi<1,2>"))},
B5(a,b,c){var s="count"
if(t.gt.b(a)){A.nS(b,s,t.S)
A.bo(b,s)
return new A.eC(a,b,c.h("eC<0>"))}A.nS(b,s,t.S)
A.bo(b,s)
return new A.cS(a,b,c.h("cS<0>"))},
hl(){return new A.ck("No element")},
At(){return new A.ck("Too few elements")},
lb(a,b,c,d,e){if(c-b<=32)A.EU(a,b,c,d,e)
else A.ET(a,b,c,d,e)},
EU(a,b,c,d,e){var s,r,q,p,o,n
for(s=b+1,r=J.aT(a);s<=c;++s){q=r.j(a,s)
p=s
for(;;){if(p>b){o=d.$2(r.j(a,p-1),q)
if(typeof o!=="number")return o.al()
o=o>0}else o=!1
if(!o)break
n=p-1
r.i(a,p,r.j(a,n))
p=n}r.i(a,p,q)}},
ET(a3,a4,a5,a6,a7){var s,r,q,p,o,n,m,l,k,j=B.c.ag(a5-a4+1,6),i=a4+j,h=a5-j,g=B.c.ag(a4+a5,2),f=g-j,e=g+j,d=J.aT(a3),c=d.j(a3,i),b=d.j(a3,f),a=d.j(a3,g),a0=d.j(a3,e),a1=d.j(a3,h),a2=a6.$2(c,b)
if(typeof a2!=="number")return a2.al()
if(a2>0){s=b
b=c
c=s}a2=a6.$2(a0,a1)
if(typeof a2!=="number")return a2.al()
if(a2>0){s=a1
a1=a0
a0=s}a2=a6.$2(c,a)
if(typeof a2!=="number")return a2.al()
if(a2>0){s=a
a=c
c=s}a2=a6.$2(b,a)
if(typeof a2!=="number")return a2.al()
if(a2>0){s=a
a=b
b=s}a2=a6.$2(c,a0)
if(typeof a2!=="number")return a2.al()
if(a2>0){s=a0
a0=c
c=s}a2=a6.$2(a,a0)
if(typeof a2!=="number")return a2.al()
if(a2>0){s=a0
a0=a
a=s}a2=a6.$2(b,a1)
if(typeof a2!=="number")return a2.al()
if(a2>0){s=a1
a1=b
b=s}a2=a6.$2(b,a)
if(typeof a2!=="number")return a2.al()
if(a2>0){s=a
a=b
b=s}a2=a6.$2(a0,a1)
if(typeof a2!=="number")return a2.al()
if(a2>0){s=a1
a1=a0
a0=s}d.i(a3,i,c)
d.i(a3,g,a)
d.i(a3,h,a1)
d.i(a3,f,d.j(a3,a4))
d.i(a3,e,d.j(a3,a5))
r=a4+1
q=a5-1
p=J.a8(a6.$2(b,a0),0)
if(p)for(o=r;o<=q;++o){n=d.j(a3,o)
m=a6.$2(n,b)
if(m===0)continue
if(m<0){if(o!==r){d.i(a3,o,d.j(a3,r))
d.i(a3,r,n)}++r}else for(;;){m=a6.$2(d.j(a3,q),b)
if(m>0){--q
continue}else{l=q-1
if(m<0){d.i(a3,o,d.j(a3,r))
k=r+1
d.i(a3,r,d.j(a3,q))
d.i(a3,q,n)
q=l
r=k
break}else{d.i(a3,o,d.j(a3,q))
d.i(a3,q,n)
q=l
break}}}}else for(o=r;o<=q;++o){n=d.j(a3,o)
if(a6.$2(n,b)<0){if(o!==r){d.i(a3,o,d.j(a3,r))
d.i(a3,r,n)}++r}else if(a6.$2(n,a0)>0)for(;;)if(a6.$2(d.j(a3,q),a0)>0){--q
if(q<o)break
continue}else{l=q-1
if(a6.$2(d.j(a3,q),b)<0){d.i(a3,o,d.j(a3,r))
k=r+1
d.i(a3,r,d.j(a3,q))
d.i(a3,q,n)
r=k}else{d.i(a3,o,d.j(a3,q))
d.i(a3,q,n)}q=l
break}}a2=r-1
d.i(a3,a4,d.j(a3,a2))
d.i(a3,a2,b)
a2=q+1
d.i(a3,a5,d.j(a3,a2))
d.i(a3,a2,a0)
A.lb(a3,a4,r-2,a6,a7)
A.lb(a3,q+2,a5,a6,a7)
if(p)return
if(r<i&&q>h){while(J.a8(a6.$2(d.j(a3,r),b),0))++r
while(J.a8(a6.$2(d.j(a3,q),a0),0))--q
for(o=r;o<=q;++o){n=d.j(a3,o)
if(a6.$2(n,b)===0){if(o!==r){d.i(a3,o,d.j(a3,r))
d.i(a3,r,n)}++r}else if(a6.$2(n,a0)===0)for(;;)if(a6.$2(d.j(a3,q),a0)===0){--q
if(q<o)break
continue}else{l=q-1
if(a6.$2(d.j(a3,q),b)<0){d.i(a3,o,d.j(a3,r))
k=r+1
d.i(a3,r,d.j(a3,q))
d.i(a3,q,n)
r=k}else{d.i(a3,o,d.j(a3,q))
d.i(a3,q,n)}q=l
break}}A.lb(a3,r,q,a6,a7)}else A.lb(a3,r,q,a6,a7)},
du:function du(){},
fY:function fY(a,b){this.a=a
this.$ti=b},
dK:function dK(a,b){this.a=a
this.$ti=b},
ia:function ia(a,b){this.a=a
this.$ti=b},
i6:function i6(){},
ub:function ub(a,b){this.a=a
this.b=b},
cw:function cw(a,b){this.a=a
this.$ti=b},
cG:function cG(a){this.a=a},
c9:function c9(a){this.a=a},
y9:function y9(){},
rC:function rC(){},
K:function K(){},
z:function z(){},
e3:function e3(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.$ti=d},
aw:function aw(a,b,c){var _=this
_.a=a
_.b=b
_.c=0
_.d=null
_.$ti=c},
bi:function bi(a,b,c){this.a=a
this.b=b
this.$ti=c},
dL:function dL(a,b,c){this.a=a
this.b=b
this.$ti=c},
hw:function hw(a,b,c){var _=this
_.a=null
_.b=a
_.c=b
_.$ti=c},
E:function E(a,b,c){this.a=a
this.b=b
this.$ti=c},
a3:function a3(a,b,c){this.a=a
this.b=b
this.$ti=c},
e9:function e9(a,b,c){this.a=a
this.b=b
this.$ti=c},
ha:function ha(a,b,c){this.a=a
this.b=b
this.$ti=c},
hb:function hb(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.d=null
_.$ti=d},
cS:function cS(a,b,c){this.a=a
this.b=b
this.$ti=c},
eC:function eC(a,b,c){this.a=a
this.b=b
this.$ti=c},
hR:function hR(a,b,c){this.a=a
this.b=b
this.$ti=c},
dM:function dM(a){this.$ti=a},
h8:function h8(a){this.$ti=a},
hZ:function hZ(a,b){this.a=a
this.$ti=b},
i_:function i_(a,b){this.a=a
this.$ti=b},
av:function av(){},
co:function co(){},
fh:function fh(){},
cN:function cN(a,b){this.a=a
this.$ti=b},
iQ:function iQ(){},
yy(a,b,c){var s,r,q,p,o,n,m,l=A.n(a),k=A.qg(new A.aW(a,l.h("aW<1>")),!0,b),j=k.length,i=0
for(;;){if(!(i<j)){s=!0
break}r=k[i]
if(typeof r!="string"||"__proto__"===r){s=!1
break}++i}if(s){q={}
for(p=0,i=0;i<k.length;k.length===j||(0,A.I)(k),++i,p=o){r=k[i]
c.a(a.j(0,r))
o=p+1
q[r]=p}n=A.qg(new A.cI(a,l.h("cI<2>")),!0,c)
m=new A.i(q,n,b.h("@<0>").A(c).h("i<1,2>"))
m.$keys=k
return m}return new A.h3(A.qe(a,b,c),b.h("@<0>").A(c).h("h3<1,2>"))},
DF(){throw A.d(A.ao("Cannot modify unmodifiable Map"))},
DG(){throw A.d(A.ao("Cannot modify constant Set"))},
CP(a){var s=v.mangledGlobalNames[a]
if(s!=null)return s
return"minified:"+a},
I1(a,b){var s
if(b!=null){s=b.x
if(s!=null)return s}return t.dX.b(a)},
w(a){var s
if(typeof a=="string")return a
if(typeof a=="number"){if(a!==0)return""+a}else if(!0===a)return"true"
else if(!1===a)return"false"
else if(a==null)return"null"
s=J.aF(a)
return s},
b0(a){var s,r=$.AM
if(r==null)r=$.AM=Symbol("identityHashCode")
s=a[r]
if(s==null){s=Math.random()*0x3fffffff|0
a[r]=s}return s},
hH(a,b){var s,r,q,p,o,n=null,m=/^\s*[+-]?((0x[a-f0-9]+)|(\d+)|([a-z0-9]+))\s*$/i.exec(a)
if(m==null)return n
if(3>=m.length)return A.f(m,3)
s=m[3]
if(b==null){if(s!=null)return parseInt(a,10)
if(m[2]!=null)return parseInt(a,16)
return n}if(b<2||b>36)throw A.d(A.an(b,2,36,"radix",n))
if(b===10&&s!=null)return parseInt(a,10)
if(b<10||s==null){r=b<=10?47+b:86+b
q=m[1]
for(p=q.length,o=0;o<p;++o)if((q.charCodeAt(o)|32)>r)return n}return parseInt(a,b)},
bZ(a){var s,r
if(!/^\s*[+-]?(?:Infinity|NaN|(?:\.\d+|\d+(?:\.\d*)?)(?:[eE][+-]?\d+)?)\s*$/.test(a))return null
s=parseFloat(a)
if(isNaN(s)){r=B.a.aG(a)
if(r==="NaN"||r==="+NaN"||r==="-NaN")return s
return null}return s},
kF(a){var s,r,q,p
if(a instanceof A.u)return A.bk(A.aX(a),null)
s=J.em(a)
if(s===B.d4||s===B.d8||t.cx.b(a)){r=B.aG(a)
if(r!=="Object"&&r!=="")return r
q=a.constructor
if(typeof q=="function"){p=q.name
if(typeof p=="string"&&p!=="Object"&&p!=="")return p}}return A.bk(A.aX(a),null)},
AT(a){var s,r,q
if(a==null||typeof a=="number"||A.xk(a))return J.aF(a)
if(typeof a=="string")return JSON.stringify(a)
if(a instanceof A.bg)return a.k(0)
if(a instanceof A.br)return a.ho(!0)
s=$.Dg()
for(r=0;r<1;++r){q=s[r].nj(a)
if(q!=null)return q}return"Instance of '"+A.kF(a)+"'"},
EA(){if(!!self.location)return self.location.href
return null},
AL(a){var s,r,q,p,o=a.length
if(o<=500)return String.fromCharCode.apply(null,a)
for(s="",r=0;r<o;r=q){q=r+500
p=q<o?q:o
s+=String.fromCharCode.apply(null,a.slice(r,p))}return s},
ED(a){var s,r,q,p=A.a([],t.lC)
for(s=a.length,r=0;r<a.length;a.length===s||(0,A.I)(a),++r){q=a[r]
if(!A.mV(q))throw A.d(A.el(q))
if(q<=65535)B.b.m(p,q)
else if(q<=1114111){B.b.m(p,55296+(B.c.bm(q-65536,10)&1023))
B.b.m(p,56320+(q&1023))}else throw A.d(A.el(q))}return A.AL(p)},
EC(a){var s,r,q
for(s=a.length,r=0;r<s;++r){q=a[r]
if(!A.mV(q))throw A.d(A.el(q))
if(q<0)throw A.d(A.el(q))
if(q>65535)return A.ED(a)}return A.AL(a)},
EE(a,b,c){var s,r,q,p
if(c<=500&&b===0&&c===a.length)return String.fromCharCode.apply(null,a)
for(s=b,r="";s<c;s=q){q=s+500
p=q<c?q:c
r+=String.fromCharCode.apply(null,a.subarray(s,p))}return r},
am(a){var s
if(0<=a){if(a<=65535)return String.fromCharCode(a)
if(a<=1114111){s=a-65536
return String.fromCharCode((B.c.bm(s,10)|55296)>>>0,s&1023|56320)}}throw A.d(A.an(a,0,1114111,null,null))},
bC(a){if(a.date===void 0)a.date=new Date(a.a)
return a.date},
kE(a){return a.c?A.bC(a).getUTCFullYear()+0:A.bC(a).getFullYear()+0},
AR(a){return a.c?A.bC(a).getUTCMonth()+1:A.bC(a).getMonth()+1},
AN(a){return a.c?A.bC(a).getUTCDate()+0:A.bC(a).getDate()+0},
AO(a){return a.c?A.bC(a).getUTCHours()+0:A.bC(a).getHours()+0},
AQ(a){return a.c?A.bC(a).getUTCMinutes()+0:A.bC(a).getMinutes()+0},
AS(a){return a.c?A.bC(a).getUTCSeconds()+0:A.bC(a).getSeconds()+0},
AP(a){return a.c?A.bC(a).getUTCMilliseconds()+0:A.bC(a).getMilliseconds()+0},
EB(a){var s=a.$thrownJsError
if(s==null)return null
return A.b3(s)},
yQ(a,b){var s
if(a.$thrownJsError==null){s=new Error()
A.aD(a,s)
a.$thrownJsError=s
s.stack=b.k(0)}},
zk(a){throw A.d(A.el(a))},
f(a,b){if(a==null)J.b4(a)
throw A.d(A.n1(a,b))},
n1(a,b){var s,r="index"
if(!A.mV(b))return new A.bI(!0,b,r,null)
s=A.bb(J.b4(a))
if(b<0||b>=s)return A.pW(b,s,a,r)
return A.qL(b,r)},
Hi(a,b,c){if(a<0||a>c)return A.an(a,0,c,"start",null)
if(b!=null)if(b<a||b>c)return A.an(b,a,c,"end",null)
return new A.bI(!0,b,"end",null)},
el(a){return new A.bI(!0,a,null,null)},
d(a){return A.aD(a,new Error())},
aD(a,b){var s
if(a==null)a=new A.cY()
b.dartException=a
s=A.Im
if("defineProperty" in Object){Object.defineProperty(b,"message",{get:s})
b.name=""}else b.toString=s
return b},
Im(){return J.aF(this.dartException)},
ak(a,b){throw A.aD(a,b==null?new Error():b)},
au(a,b,c){var s
if(b==null)b=0
if(c==null)c=0
s=Error()
A.ak(A.Gd(a,b,c),s)},
Gd(a,b,c){var s,r,q,p,o,n,m,l,k
if(typeof b=="string")s=b
else{r="[]=;add;removeWhere;retainWhere;removeRange;setRange;setInt8;setInt16;setInt32;setUint8;setUint16;setUint32;setFloat32;setFloat64".split(";")
q=r.length
p=b
if(p>q){c=p/q|0
p%=q}s=r[p]}o=typeof c=="string"?c:"modify;remove from;add to".split(";")[c]
n=t._.b(a)?"list":"ByteData"
m=a.$flags|0
l="a "
if((m&4)!==0)k="constant "
else if((m&2)!==0){k="unmodifiable "
l="an "}else k=(m&1)!==0?"fixed-length ":""
return new A.hY("'"+s+"': Cannot "+o+" "+l+k+n)},
I(a){throw A.d(A.aB(a))},
cZ(a){var s,r,q,p,o,n
a=A.ye(a.replace(String({}),"$receiver$"))
s=a.match(/\\\$[a-zA-Z]+\\\$/g)
if(s==null)s=A.a([],t.s)
r=s.indexOf("\\$arguments\\$")
q=s.indexOf("\\$argumentsExpr\\$")
p=s.indexOf("\\$expr\\$")
o=s.indexOf("\\$method\\$")
n=s.indexOf("\\$receiver\\$")
return new A.te(a.replace(new RegExp("\\\\\\$arguments\\\\\\$","g"),"((?:x|[^x])*)").replace(new RegExp("\\\\\\$argumentsExpr\\\\\\$","g"),"((?:x|[^x])*)").replace(new RegExp("\\\\\\$expr\\\\\\$","g"),"((?:x|[^x])*)").replace(new RegExp("\\\\\\$method\\\\\\$","g"),"((?:x|[^x])*)").replace(new RegExp("\\\\\\$receiver\\\\\\$","g"),"((?:x|[^x])*)"),r,q,p,o,n)},
tf(a){return function($expr$){var $argumentsExpr$="$arguments$"
try{$expr$.$method$($argumentsExpr$)}catch(s){return s.message}}(a)},
Ba(a){return function($expr$){try{$expr$.$method$}catch(s){return s.message}}(a)},
yM(a,b){var s=b==null,r=s?null:b.method
return new A.ka(a,r,s?null:b.receiver)},
a1(a){var s
if(a==null)return new A.ku(a)
if(a instanceof A.h9){s=a.a
return A.dD(a,s==null?A.az(s):s)}if(typeof a!=="object")return a
if("dartException" in a)return A.dD(a,a.dartException)
return A.GX(a)},
dD(a,b){if(t.B.b(b))if(b.$thrownJsError==null)b.$thrownJsError=a
return b},
GX(a){var s,r,q,p,o,n,m,l,k,j,i,h,g
if(!("message" in a))return a
s=a.message
if("number" in a&&typeof a.number=="number"){r=a.number
q=r&65535
if((B.c.bm(r,16)&8191)===10)switch(q){case 438:return A.dD(a,A.yM(A.w(s)+" (Error "+q+")",null))
case 445:case 5007:A.w(s)
return A.dD(a,new A.hC())}}if(a instanceof TypeError){p=$.CV()
o=$.CW()
n=$.CX()
m=$.CY()
l=$.D0()
k=$.D1()
j=$.D_()
$.CZ()
i=$.D3()
h=$.D2()
g=p.aM(s)
if(g!=null)return A.dD(a,A.yM(A.r(s),g))
else{g=o.aM(s)
if(g!=null){g.method="call"
return A.dD(a,A.yM(A.r(s),g))}else if(n.aM(s)!=null||m.aM(s)!=null||l.aM(s)!=null||k.aM(s)!=null||j.aM(s)!=null||m.aM(s)!=null||i.aM(s)!=null||h.aM(s)!=null){A.r(s)
return A.dD(a,new A.hC())}}return A.dD(a,new A.lB(typeof s=="string"?s:""))}if(a instanceof RangeError){if(typeof s=="string"&&s.indexOf("call stack")!==-1)return new A.hS()
s=function(b){try{return String(b)}catch(f){}return null}(a)
return A.dD(a,new A.bI(!1,null,null,typeof s=="string"?s.replace(/^RangeError:\s*/,""):s))}if(typeof InternalError=="function"&&a instanceof InternalError)if(typeof s=="string"&&s==="too much recursion")return new A.hS()
return a},
b3(a){var s
if(a instanceof A.h9)return a.b
if(a==null)return new A.iB(a)
s=a.$cachedTrace
if(s!=null)return s
s=new A.iB(a)
if(typeof a==="object")a.$cachedTrace=s
return s},
eo(a){if(a==null)return J.Z(a)
if(typeof a=="object")return A.b0(a)
return J.Z(a)},
Hn(a,b){var s,r,q,p=a.length
for(s=0;s<p;s=q){r=s+1
q=r+1
b.i(0,a[s],a[r])}return b},
Ho(a,b){var s,r=a.length
for(s=0;s<r;++s)b.m(0,a[s])
return b},
Gs(a,b,c,d,e,f){t.gY.a(a)
switch(A.bb(b)){case 0:return a.$0()
case 1:return a.$1(c)
case 2:return a.$2(c,d)
case 3:return a.$3(c,d,e)
case 4:return a.$4(c,d,e,f)}throw A.d(A.DS("Unsupported number of arguments for wrapped closure"))},
fG(a,b){var s=a.$identity
if(!!s)return s
s=A.Ha(a,b)
a.$identity=s
return s},
Ha(a,b){var s
switch(b){case 0:s=a.$0
break
case 1:s=a.$1
break
case 2:s=a.$2
break
case 3:s=a.$3
break
case 4:s=a.$4
break
default:s=null}if(s!=null)return s.bind(a)
return function(c,d,e){return function(f,g,h,i){return e(c,d,f,g,h,i)}}(a,b,A.Gs)},
DC(a2){var s,r,q,p,o,n,m,l,k,j,i=a2.co,h=a2.iS,g=a2.iI,f=a2.nDA,e=a2.aI,d=a2.fs,c=a2.cs,b=d[0],a=c[0],a0=i[b],a1=a2.fT
a1.toString
s=h?Object.create(new A.lj().constructor.prototype):Object.create(new A.ex(null,null).constructor.prototype)
s.$initialize=s.constructor
r=h?function static_tear_off(){this.$initialize()}:function tear_off(a3,a4){this.$initialize(a3,a4)}
s.constructor=r
r.prototype=s
s.$_name=b
s.$_target=a0
q=!h
if(q)p=A.Aj(b,a0,g,f)
else{s.$static_name=b
p=a0}s.$S=A.Dy(a1,h,g)
s[a]=p
for(o=p,n=1;n<d.length;++n){m=d[n]
if(typeof m=="string"){l=i[m]
k=m
m=l}else k=""
j=c[n]
if(j!=null){if(q)m=A.Aj(k,m,g,f)
s[j]=m}if(n===e)o=m}s.$C=o
s.$R=a2.rC
s.$D=a2.dV
return r},
Dy(a,b,c){if(typeof a=="number")return a
if(typeof a=="string"){if(b)throw A.d("Cannot compute signature for static tearoff.")
return function(d,e){return function(){return e(this,d)}}(a,A.Du)}throw A.d("Error in functionType of tearoff")},
Dz(a,b,c,d){var s=A.Af
switch(b?-1:a){case 0:return function(e,f){return function(){return f(this)[e]()}}(c,s)
case 1:return function(e,f){return function(g){return f(this)[e](g)}}(c,s)
case 2:return function(e,f){return function(g,h){return f(this)[e](g,h)}}(c,s)
case 3:return function(e,f){return function(g,h,i){return f(this)[e](g,h,i)}}(c,s)
case 4:return function(e,f){return function(g,h,i,j){return f(this)[e](g,h,i,j)}}(c,s)
case 5:return function(e,f){return function(g,h,i,j,k){return f(this)[e](g,h,i,j,k)}}(c,s)
default:return function(e,f){return function(){return e.apply(f(this),arguments)}}(d,s)}},
Aj(a,b,c,d){if(c)return A.DB(a,b,d)
return A.Dz(b.length,d,a,b)},
DA(a,b,c,d){var s=A.Af,r=A.Dv
switch(b?-1:a){case 0:throw A.d(new A.kR("Intercepted function with no arguments."))
case 1:return function(e,f,g){return function(){return f(this)[e](g(this))}}(c,r,s)
case 2:return function(e,f,g){return function(h){return f(this)[e](g(this),h)}}(c,r,s)
case 3:return function(e,f,g){return function(h,i){return f(this)[e](g(this),h,i)}}(c,r,s)
case 4:return function(e,f,g){return function(h,i,j){return f(this)[e](g(this),h,i,j)}}(c,r,s)
case 5:return function(e,f,g){return function(h,i,j,k){return f(this)[e](g(this),h,i,j,k)}}(c,r,s)
case 6:return function(e,f,g){return function(h,i,j,k,l){return f(this)[e](g(this),h,i,j,k,l)}}(c,r,s)
default:return function(e,f,g){return function(){var q=[g(this)]
Array.prototype.push.apply(q,arguments)
return e.apply(f(this),q)}}(d,r,s)}},
DB(a,b,c){var s,r
if($.Ad==null)$.Ad=A.Ac("interceptor")
if($.Ae==null)$.Ae=A.Ac("receiver")
s=b.length
r=A.DA(s,c,a,b)
return r},
zg(a){return A.DC(a)},
Du(a,b){return A.iK(v.typeUniverse,A.aX(a.a),b)},
Af(a){return a.a},
Dv(a){return a.b},
Ac(a){var s,r,q,p=new A.ex("receiver","interceptor"),o=Object.getOwnPropertyNames(p)
o.$flags=1
s=o
for(o=s.length,r=0;r<o;++r){q=s[r]
if(p[q]===a)return q}throw A.d(A.ai("Field name "+a+" not found.",null))},
Hq(a){return v.getIsolateTag(a)},
yj(){return v.G},
J8(a,b,c){Object.defineProperty(a,b,{value:c,enumerable:false,writable:true,configurable:true})},
I2(a){var s,r,q,p,o,n=A.r($.CA.$1(a)),m=$.xW[n]
if(m!=null){Object.defineProperty(a,v.dispatchPropertyName,{value:m,enumerable:false,writable:true,configurable:true})
return m.i}s=$.y5[n]
if(s!=null)return s
r=v.interceptorsByTag[n]
if(r==null){q=A.aA($.Cn.$2(a,n))
if(q!=null){m=$.xW[q]
if(m!=null){Object.defineProperty(a,v.dispatchPropertyName,{value:m,enumerable:false,writable:true,configurable:true})
return m.i}s=$.y5[q]
if(s!=null)return s
r=v.interceptorsByTag[q]
n=q}}if(r==null)return null
s=r.prototype
p=n[0]
if(p==="!"){m=A.y8(s)
$.xW[n]=m
Object.defineProperty(a,v.dispatchPropertyName,{value:m,enumerable:false,writable:true,configurable:true})
return m.i}if(p==="~"){$.y5[n]=s
return s}if(p==="-"){o=A.y8(s)
Object.defineProperty(Object.getPrototypeOf(a),v.dispatchPropertyName,{value:o,enumerable:false,writable:true,configurable:true})
return o.i}if(p==="+")return A.CI(a,s)
if(p==="*")throw A.d(A.yW(n))
if(v.leafTags[n]===true){o=A.y8(s)
Object.defineProperty(Object.getPrototypeOf(a),v.dispatchPropertyName,{value:o,enumerable:false,writable:true,configurable:true})
return o.i}else return A.CI(a,s)},
CI(a,b){var s=Object.getPrototypeOf(a)
Object.defineProperty(s,v.dispatchPropertyName,{value:J.zp(b,s,null,null),enumerable:false,writable:true,configurable:true})
return b},
y8(a){return J.zp(a,!1,null,!!a.$iby)},
I5(a,b,c){var s=b.prototype
if(v.leafTags[a]===true)return A.y8(s)
else return J.zp(s,c,null,null)},
HW(){if(!0===$.zl)return
$.zl=!0
A.HX()},
HX(){var s,r,q,p,o,n,m,l
$.xW=Object.create(null)
$.y5=Object.create(null)
A.HV()
s=v.interceptorsByTag
r=Object.getOwnPropertyNames(s)
if(typeof window!="undefined"){window
q=function(){}
for(p=0;p<r.length;++p){o=r[p]
n=$.CK.$1(o)
if(n!=null){m=A.I5(o,s[o],n)
if(m!=null){Object.defineProperty(n,v.dispatchPropertyName,{value:m,enumerable:false,writable:true,configurable:true})
q.prototype=n}}}}for(p=0;p<r.length;++p){o=r[p]
if(/^[A-Za-z_]/.test(o)){l=s[o]
s["!"+o]=l
s["~"+o]=l
s["-"+o]=l
s["+"+o]=l
s["*"+o]=l}}},
HV(){var s,r,q,p,o,n,m=B.ch()
m=A.fD(B.ci,A.fD(B.cj,A.fD(B.aH,A.fD(B.aH,A.fD(B.ck,A.fD(B.cl,A.fD(B.cm(B.aG),m)))))))
if(typeof dartNativeDispatchHooksTransformer!="undefined"){s=dartNativeDispatchHooksTransformer
if(typeof s=="function")s=[s]
if(Array.isArray(s))for(r=0;r<s.length;++r){q=s[r]
if(typeof q=="function")m=q(m)||m}}p=m.getTag
o=m.getUnknownTag
n=m.prototypeForTag
$.CA=new A.y2(p)
$.Cn=new A.y3(o)
$.CK=new A.y4(n)},
fD(a,b){return a(b)||b},
Fy(a,b){var s,r
for(s=0;s<a.length;++s){r=a[s]
if(!(s<b.length))return A.f(b,s)
if(!J.a8(r,b[s]))return!1}return!0},
Hh(a,b){var s=b.length,r=v.rttc[""+s+";"+a]
if(r==null)return null
if(s===0)return r
if(s===r.length)return r.apply(null,b)
return r(b)},
yK(a,b,c,d,e,f){var s=b?"m":"",r=c?"":"i",q=d?"u":"",p=e?"s":"",o=function(g,h){try{return new RegExp(g,h)}catch(n){return n}}(a,s+r+q+p+f)
if(o instanceof RegExp)return o
throw A.d(A.ap("Illegal RegExp pattern ("+String(o)+")",a,null))},
If(a,b,c){var s
if(typeof b=="string")return a.indexOf(b,c)>=0
else if(b instanceof A.dP){s=B.a.S(a,c)
return b.b.test(s)}else return!J.zA(b,B.a.S(a,c)).gL(0)},
Cx(a){if(a.indexOf("$",0)>=0)return a.replace(/\$/g,"$$$$")
return a},
ye(a){if(/[[\]{}()*+?.\\^$|]/.test(a))return a.replace(/[[\]{}()*+?.\\^$|]/g,"\\$&")
return a},
d8(a,b,c){var s
if(typeof b=="string")return A.Ih(a,b,c)
if(b instanceof A.dP){s=b.gfZ()
s.lastIndex=0
return a.replace(s,A.Cx(c))}return A.Ig(a,b,c)},
Ig(a,b,c){var s,r,q,p
for(s=J.zA(b,a),s=s.gC(s),r=0,q="";s.p();){p=s.gu()
q=q+a.substring(r,p.gG())+c
r=p.gF()}s=q+a.substring(r)
return s.charCodeAt(0)==0?s:s},
Ih(a,b,c){var s,r,q
if(b===""){if(a==="")return c
s=a.length
for(r=c,q=0;q<s;++q)r=r+a[q]+c
return r.charCodeAt(0)==0?r:r}if(a.indexOf(b,0)<0)return a
if(a.length<500||c.indexOf("$",0)>=0)return a.split(b).join(c)
return a.replace(new RegExp(A.ye(b),"g"),A.Cx(c))},
Ck(a){return a},
CM(a,b,c,d){var s,r,q,p,o,n,m
for(s=b.bF(0,a),s=new A.ds(s.a,s.b,s.c),r=t.F,q=0,p="";s.p();){o=s.d
if(o==null)o=r.a(o)
n=o.b
m=n.index
p=p+A.w(A.Ck(B.a.q(a,q,m)))+A.w(c.$1(o))
q=m+n[0].length}s=p+A.w(A.Ck(B.a.S(a,q)))
return s.charCodeAt(0)==0?s:s},
Ij(a,b,c,d){var s=a.indexOf(b,d)
if(s<0)return a
return A.CN(a,s,s+b.length,c)},
Ii(a,b,c,d){var s,r,q=b.d8(0,a,d),p=new A.ds(q.a,q.b,q.c)
if(!p.p())return a
s=p.d
if(s==null)s=t.F.a(s)
r=A.w(c.$1(s))
return B.a.bf(a,s.b.index,s.gF(),r)},
CN(a,b,c,d){return a.substring(0,b)+d+a.substring(c)},
A:function A(a,b){this.a=a
this.b=b},
bQ:function bQ(a,b,c){this.a=a
this.b=b
this.c=c},
ei:function ei(a,b,c){this.a=a
this.b=b
this.c=c},
b2:function b2(a){this.a=a},
ej:function ej(a){this.a=a},
iu:function iu(a){this.a=a},
h3:function h3(a,b){this.a=a
this.$ti=b},
h2:function h2(){},
ou:function ou(a,b,c){this.a=a
this.b=b
this.c=c},
i:function i(a,b,c){this.a=a
this.b=b
this.$ti=c},
ij:function ij(a,b){this.a=a
this.$ti=b},
ee:function ee(a,b,c){var _=this
_.a=a
_.b=b
_.c=0
_.d=null
_.$ti=c},
h4:function h4(){},
h5:function h5(a,b,c){this.a=a
this.b=b
this.$ti=c},
k4:function k4(){},
eO:function eO(a,b){this.a=a
this.$ti=b},
hM:function hM(){},
te:function te(a,b,c,d,e,f){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f},
hC:function hC(){},
ka:function ka(a,b,c){this.a=a
this.b=b
this.c=c},
lB:function lB(a){this.a=a},
ku:function ku(a){this.a=a},
h9:function h9(a,b){this.a=a
this.b=b},
iB:function iB(a){this.a=a
this.b=null},
bg:function bg(){},
ju:function ju(){},
jv:function jv(){},
lq:function lq(){},
lj:function lj(){},
ex:function ex(a,b){this.a=a
this.b=b},
kR:function kR(a){this.a=a},
bz:function bz(a){var _=this
_.a=0
_.f=_.e=_.d=_.c=_.b=null
_.r=0
_.$ti=a},
q0:function q0(a){this.a=a},
qd:function qd(a,b){var _=this
_.a=a
_.b=b
_.d=_.c=null},
aW:function aW(a,b){this.a=a
this.$ti=b},
hv:function hv(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.d=null
_.$ti=d},
cI:function cI(a,b){this.a=a
this.$ti=b},
bh:function bh(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.d=null
_.$ti=d},
aC:function aC(a,b){this.a=a
this.$ti=b},
cH:function cH(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.d=null
_.$ti=d},
hs:function hs(a){var _=this
_.a=0
_.f=_.e=_.d=_.c=_.b=null
_.r=0
_.$ti=a},
y2:function y2(a){this.a=a},
y3:function y3(a){this.a=a},
y4:function y4(a){this.a=a},
br:function br(){},
fq:function fq(){},
eh:function eh(){},
dx:function dx(){},
dP:function dP(a,b){var _=this
_.a=a
_.b=b
_.e=_.d=_.c=null},
fp:function fp(a){this.b=a},
lP:function lP(a,b,c){this.a=a
this.b=b
this.c=c},
ds:function ds(a,b,c){var _=this
_.a=a
_.b=b
_.c=c
_.d=null},
hV:function hV(a,b){this.a=a
this.c=b},
mH:function mH(a,b,c){this.a=a
this.b=b
this.c=c},
mI:function mI(a,b,c){var _=this
_.a=a
_.b=b
_.c=c
_.d=null},
Ik(a){throw A.aD(A.Az(a),new Error())},
S(){throw A.aD(A.Em(""),new Error())},
bT(){throw A.aD(A.El(""),new Error())},
fI(){throw A.aD(A.Az(""),new Error())},
Bk(){var s=new A.uc()
return s.b=s},
uc:function uc(){this.b=null},
C_(a){return a},
Es(a){return new Int8Array(a)},
AD(a){return new Uint8Array(a)},
d7(a,b,c){if(a>>>0!==a||a>=c)throw A.d(A.n1(b,a))},
BX(a,b,c){var s
if(!(a>>>0!==a))s=b>>>0!==b||a>b||b>c
else s=!0
if(s)throw A.d(A.Hi(a,b,c))
return b},
eY:function eY(){},
hz:function hz(){},
kl:function kl(){},
b_:function b_(){},
hy:function hy(){},
bA:function bA(){},
km:function km(){},
kn:function kn(){},
ko:function ko(){},
kp:function kp(){},
kq:function kq(){},
ks:function ks(){},
hA:function hA(){},
hB:function hB(){},
dT:function dT(){},
io:function io(){},
ip:function ip(){},
iq:function iq(){},
ir:function ir(){},
yT(a,b){var s=b.c
return s==null?b.c=A.iI(a,"ae",[b.x]):s},
B_(a){var s=a.w
if(s===6||s===7)return A.B_(a.x)
return s===11||s===12},
EQ(a){return a.as},
zr(a,b){var s,r=b.length
for(s=0;s<r;++s)if(!a[s].b(b[s]))return!1
return!0},
aJ(a){return A.wS(v.typeUniverse,a,!1)},
I_(a,b){var s,r,q,p,o
if(a==null)return null
s=b.y
r=a.Q
if(r==null)r=a.Q=new Map()
q=b.as
p=r.get(q)
if(p!=null)return p
o=A.dB(v.typeUniverse,a.x,s,0)
r.set(q,o)
return o},
dB(a1,a2,a3,a4){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e,d,c,b,a,a0=a2.w
switch(a0){case 5:case 1:case 2:case 3:case 4:return a2
case 6:s=a2.x
r=A.dB(a1,s,a3,a4)
if(r===s)return a2
return A.BA(a1,r,!0)
case 7:s=a2.x
r=A.dB(a1,s,a3,a4)
if(r===s)return a2
return A.Bz(a1,r,!0)
case 8:q=a2.y
p=A.fC(a1,q,a3,a4)
if(p===q)return a2
return A.iI(a1,a2.x,p)
case 9:o=a2.x
n=A.dB(a1,o,a3,a4)
m=a2.y
l=A.fC(a1,m,a3,a4)
if(n===o&&l===m)return a2
return A.z3(a1,n,l)
case 10:k=a2.x
j=a2.y
i=A.fC(a1,j,a3,a4)
if(i===j)return a2
return A.BB(a1,k,i)
case 11:h=a2.x
g=A.dB(a1,h,a3,a4)
f=a2.y
e=A.GT(a1,f,a3,a4)
if(g===h&&e===f)return a2
return A.By(a1,g,e)
case 12:d=a2.y
a4+=d.length
c=A.fC(a1,d,a3,a4)
o=a2.x
n=A.dB(a1,o,a3,a4)
if(c===d&&n===o)return a2
return A.z4(a1,n,c,!0)
case 13:b=a2.x
if(b<a4)return a2
a=a3[b-a4]
if(a==null)return a2
return a
default:throw A.d(A.jg("Attempted to substitute unexpected RTI kind "+a0))}},
fC(a,b,c,d){var s,r,q,p,o=b.length,n=A.wZ(o)
for(s=!1,r=0;r<o;++r){q=b[r]
p=A.dB(a,q,c,d)
if(p!==q)s=!0
n[r]=p}return s?n:b},
GU(a,b,c,d){var s,r,q,p,o,n,m=b.length,l=A.wZ(m)
for(s=!1,r=0;r<m;r+=3){q=b[r]
p=b[r+1]
o=b[r+2]
n=A.dB(a,o,c,d)
if(n!==o)s=!0
l.splice(r,3,q,p,n)}return s?l:b},
GT(a,b,c,d){var s,r=b.a,q=A.fC(a,r,c,d),p=b.b,o=A.fC(a,p,c,d),n=b.c,m=A.GU(a,n,c,d)
if(q===r&&o===p&&m===n)return b
s=new A.mf()
s.a=q
s.b=o
s.c=m
return s},
a(a,b){a[v.arrayRti]=b
return a},
n_(a){var s=a.$S
if(s!=null){if(typeof s=="number")return A.Hr(s)
return a.$S()}return null},
HZ(a,b){var s
if(A.B_(b))if(a instanceof A.bg){s=A.n_(a)
if(s!=null)return s}return A.aX(a)},
aX(a){if(a instanceof A.u)return A.n(a)
if(Array.isArray(a))return A.F(a)
return A.za(J.em(a))},
F(a){var s=a[v.arrayRti],r=t.dG
if(s==null)return r
if(s.constructor!==r.constructor)return r
return s},
n(a){var s=a.$ti
return s!=null?s:A.za(a)},
za(a){var s=a.constructor,r=s.$ccache
if(r!=null)return r
return A.Gp(a,s)},
Gp(a,b){var s=a instanceof A.bg?Object.getPrototypeOf(Object.getPrototypeOf(a)).constructor:b,r=A.FM(v.typeUniverse,s.name)
b.$ccache=r
return r},
Hr(a){var s,r=v.types,q=r[a]
if(typeof q=="string"){s=A.wS(v.typeUniverse,q,!1)
r[a]=s
return s}return q},
bH(a){return A.bc(A.n(a))},
zj(a){var s=A.n_(a)
return A.bc(s==null?A.aX(a):s)},
ze(a){var s
if(a instanceof A.br)return a.fR()
s=a instanceof A.bg?A.n_(a):null
if(s!=null)return s
if(t.dH.b(a))return J.yo(a).a
if(Array.isArray(a))return A.F(a)
return A.aX(a)},
bc(a){var s=a.r
return s==null?a.r=new A.mN(a):s},
Hk(a,b){var s,r,q=b,p=q.length
if(p===0)return t.aK
if(0>=p)return A.f(q,0)
s=A.iK(v.typeUniverse,A.ze(q[0]),"@<0>")
for(r=1;r<p;++r){if(!(r<q.length))return A.f(q,r)
s=A.BC(v.typeUniverse,s,A.ze(q[r]))}return A.iK(v.typeUniverse,s,a)},
bu(a){return A.bc(A.wS(v.typeUniverse,a,!1))},
Go(a){var s=this
s.b=A.GQ(s)
return s.b(a)},
GQ(a){var s,r,q,p,o
if(a===t.K)return A.Gy
if(A.en(a))return A.GC
s=a.w
if(s===6)return A.Gk
if(s===1)return A.C7
if(s===7)return A.Gt
r=A.GP(a)
if(r!=null)return r
if(s===8){q=a.x
if(a.y.every(A.en)){a.f="$i"+q
if(q==="q")return A.Gw
if(a===t.m)return A.Gv
return A.GB}}else if(s===10){p=A.Hh(a.x,a.y)
o=p==null?A.C7:p
return o==null?A.az(o):o}return A.Gi},
GP(a){if(a.w===8){if(a===t.S)return A.mV
if(a===t.r||a===t.cZ)return A.Gx
if(a===t.N)return A.GA
if(a===t.k4)return A.xk}return null},
Gn(a){var s=this,r=A.Gh
if(A.en(s))r=A.G1
else if(s===t.K)r=A.az
else if(A.fH(s)){r=A.Gj
if(s===t.aV)r=A.BU
else if(s===t.jv)r=A.aA
else if(s===t.fU)r=A.x8
else if(s===t.jh)r=A.BV
else if(s===t.jX)r=A.G0
else if(s===t.mU)r=A.a7}else if(s===t.S)r=A.bb
else if(s===t.N)r=A.r
else if(s===t.k4)r=A.dz
else if(s===t.cZ)r=A.at
else if(s===t.r)r=A.x9
else if(s===t.m)r=A.p
s.a=r
return s.a(a)},
Gi(a){var s=this
if(a==null)return A.fH(s)
return A.CG(v.typeUniverse,A.HZ(a,s),s)},
Gk(a){if(a==null)return!0
return this.x.b(a)},
GB(a){var s,r=this
if(a==null)return A.fH(r)
s=r.f
if(a instanceof A.u)return!!a[s]
return!!J.em(a)[s]},
Gw(a){var s,r=this
if(a==null)return A.fH(r)
if(typeof a!="object")return!1
if(Array.isArray(a))return!0
s=r.f
if(a instanceof A.u)return!!a[s]
return!!J.em(a)[s]},
Gv(a){var s=this
if(a==null)return!1
if(typeof a=="object"){if(a instanceof A.u)return!!a[s.f]
return!0}if(typeof a=="function")return!0
return!1},
C6(a){if(typeof a=="object"){if(a instanceof A.u)return t.m.b(a)
return!0}if(typeof a=="function")return!0
return!1},
Gh(a){var s=this
if(a==null){if(A.fH(s))return a}else if(s.b(a))return a
throw A.aD(A.C0(a,s),new Error())},
Gj(a){var s=this
if(a==null||s.b(a))return a
throw A.aD(A.C0(a,s),new Error())},
C0(a,b){return new A.fu("TypeError: "+A.Bm(a,A.bk(b,null)))},
Cr(a,b,c,d){if(A.CG(v.typeUniverse,a,b))return a
throw A.aD(A.FE("The type argument '"+A.bk(a,null)+"' is not a subtype of the type variable bound '"+A.bk(b,null)+"' of type variable '"+c+"' in '"+d+"'."),new Error())},
Bm(a,b){return A.jN(a)+": type '"+A.bk(A.ze(a),null)+"' is not a subtype of type '"+b+"'"},
FE(a){return new A.fu("TypeError: "+a)},
bS(a,b){return new A.fu("TypeError: "+A.Bm(a,b))},
Gt(a){var s=this
return s.x.b(a)||A.yT(v.typeUniverse,s).b(a)},
Gy(a){return a!=null},
az(a){if(a!=null)return a
throw A.aD(A.bS(a,"Object"),new Error())},
GC(a){return!0},
G1(a){return a},
C7(a){return!1},
xk(a){return!0===a||!1===a},
dz(a){if(!0===a)return!0
if(!1===a)return!1
throw A.aD(A.bS(a,"bool"),new Error())},
x8(a){if(!0===a)return!0
if(!1===a)return!1
if(a==null)return a
throw A.aD(A.bS(a,"bool?"),new Error())},
x9(a){if(typeof a=="number")return a
throw A.aD(A.bS(a,"double"),new Error())},
G0(a){if(typeof a=="number")return a
if(a==null)return a
throw A.aD(A.bS(a,"double?"),new Error())},
mV(a){return typeof a=="number"&&Math.floor(a)===a},
bb(a){if(typeof a=="number"&&Math.floor(a)===a)return a
throw A.aD(A.bS(a,"int"),new Error())},
BU(a){if(typeof a=="number"&&Math.floor(a)===a)return a
if(a==null)return a
throw A.aD(A.bS(a,"int?"),new Error())},
Gx(a){return typeof a=="number"},
at(a){if(typeof a=="number")return a
throw A.aD(A.bS(a,"num"),new Error())},
BV(a){if(typeof a=="number")return a
if(a==null)return a
throw A.aD(A.bS(a,"num?"),new Error())},
GA(a){return typeof a=="string"},
r(a){if(typeof a=="string")return a
throw A.aD(A.bS(a,"String"),new Error())},
aA(a){if(typeof a=="string")return a
if(a==null)return a
throw A.aD(A.bS(a,"String?"),new Error())},
p(a){if(A.C6(a))return a
throw A.aD(A.bS(a,"JSObject"),new Error())},
a7(a){if(a==null)return a
if(A.C6(a))return a
throw A.aD(A.bS(a,"JSObject?"),new Error())},
Cg(a,b){var s,r,q
for(s="",r="",q=0;q<a.length;++q,r=", ")s+=r+A.bk(a[q],b)
return s},
GL(a,b){var s,r,q,p,o,n,m=a.x,l=a.y
if(""===m)return"("+A.Cg(l,b)+")"
s=l.length
r=m.split(",")
q=r.length-s
for(p="(",o="",n=0;n<s;++n,o=", "){p+=o
if(q===0)p+="{"
p+=A.bk(l[n],b)
if(q>=0)p+=" "+r[q];++q}return p+"})"},
C2(a3,a4,a5){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e,d,c,b,a,a0,a1=", ",a2=null
if(a5!=null){s=a5.length
if(a4==null)a4=A.a([],t.s)
else a2=a4.length
r=a4.length
for(q=s;q>0;--q)B.b.m(a4,"T"+(r+q))
for(p=t.X,o="<",n="",q=0;q<s;++q,n=a1){m=a4.length
l=m-1-q
if(!(l>=0))return A.f(a4,l)
o=o+n+a4[l]
k=a5[q]
j=k.w
if(!(j===2||j===3||j===4||j===5||k===p))o+=" extends "+A.bk(k,a4)}o+=">"}else o=""
p=a3.x
i=a3.y
h=i.a
g=h.length
f=i.b
e=f.length
d=i.c
c=d.length
b=A.bk(p,a4)
for(a="",a0="",q=0;q<g;++q,a0=a1)a+=a0+A.bk(h[q],a4)
if(e>0){a+=a0+"["
for(a0="",q=0;q<e;++q,a0=a1)a+=a0+A.bk(f[q],a4)
a+="]"}if(c>0){a+=a0+"{"
for(a0="",q=0;q<c;q+=3,a0=a1){a+=a0
if(d[q+1])a+="required "
a+=A.bk(d[q+2],a4)+" "+d[q]}a+="}"}if(a2!=null){a4.toString
a4.length=a2}return o+"("+a+") => "+b},
bk(a,b){var s,r,q,p,o,n,m,l=a.w
if(l===5)return"erased"
if(l===2)return"dynamic"
if(l===3)return"void"
if(l===1)return"Never"
if(l===4)return"any"
if(l===6){s=a.x
r=A.bk(s,b)
q=s.w
return(q===11||q===12?"("+r+")":r)+"?"}if(l===7)return"FutureOr<"+A.bk(a.x,b)+">"
if(l===8){p=A.GW(a.x)
o=a.y
return o.length>0?p+("<"+A.Cg(o,b)+">"):p}if(l===10)return A.GL(a,b)
if(l===11)return A.C2(a,b,null)
if(l===12)return A.C2(a.x,b,a.y)
if(l===13){n=a.x
m=b.length
n=m-1-n
if(!(n>=0&&n<m))return A.f(b,n)
return b[n]}return"?"},
GW(a){var s=v.mangledGlobalNames[a]
if(s!=null)return s
return"minified:"+a},
FN(a,b){var s=a.tR[b]
while(typeof s=="string")s=a.tR[s]
return s},
FM(a,b){var s,r,q,p,o,n=a.eT,m=n[b]
if(m==null)return A.wS(a,b,!1)
else if(typeof m=="number"){s=m
r=A.iJ(a,5,"#")
q=A.wZ(s)
for(p=0;p<s;++p)q[p]=r
o=A.iI(a,b,q)
n[b]=o
return o}else return m},
FL(a,b){return A.BQ(a.tR,b)},
FK(a,b){return A.BQ(a.eT,b)},
wS(a,b,c){var s,r=a.eC,q=r.get(b)
if(q!=null)return q
s=A.Bu(A.Bs(a,null,b,!1))
r.set(b,s)
return s},
iK(a,b,c){var s,r,q=b.z
if(q==null)q=b.z=new Map()
s=q.get(c)
if(s!=null)return s
r=A.Bu(A.Bs(a,b,c,!0))
q.set(c,r)
return r},
BC(a,b,c){var s,r,q,p=b.Q
if(p==null)p=b.Q=new Map()
s=c.as
r=p.get(s)
if(r!=null)return r
q=A.z3(a,b,c.w===9?c.y:[c])
p.set(s,q)
return q},
dy(a,b){b.a=A.Gn
b.b=A.Go
return b},
iJ(a,b,c){var s,r,q=a.eC.get(c)
if(q!=null)return q
s=new A.c_(null,null)
s.w=b
s.as=c
r=A.dy(a,s)
a.eC.set(c,r)
return r},
BA(a,b,c){var s,r=b.as+"?",q=a.eC.get(r)
if(q!=null)return q
s=A.FI(a,b,r,c)
a.eC.set(r,s)
return s},
FI(a,b,c,d){var s,r,q
if(d){s=b.w
r=!0
if(!A.en(b))if(!(b===t.a||b===t.bE))if(s!==6)r=s===7&&A.fH(b.x)
if(r)return b
else if(s===1)return t.a}q=new A.c_(null,null)
q.w=6
q.x=b
q.as=c
return A.dy(a,q)},
Bz(a,b,c){var s,r=b.as+"/",q=a.eC.get(r)
if(q!=null)return q
s=A.FG(a,b,r,c)
a.eC.set(r,s)
return s},
FG(a,b,c,d){var s,r
if(d){s=b.w
if(A.en(b)||b===t.K)return b
else if(s===1)return A.iI(a,"ae",[b])
else if(b===t.a||b===t.bE)return t.gK}r=new A.c_(null,null)
r.w=7
r.x=b
r.as=c
return A.dy(a,r)},
FJ(a,b){var s,r,q=""+b+"^",p=a.eC.get(q)
if(p!=null)return p
s=new A.c_(null,null)
s.w=13
s.x=b
s.as=q
r=A.dy(a,s)
a.eC.set(q,r)
return r},
iH(a){var s,r,q,p=a.length
for(s="",r="",q=0;q<p;++q,r=",")s+=r+a[q].as
return s},
FF(a){var s,r,q,p,o,n=a.length
for(s="",r="",q=0;q<n;q+=3,r=","){p=a[q]
o=a[q+1]?"!":":"
s+=r+p+o+a[q+2].as}return s},
iI(a,b,c){var s,r,q,p=b
if(c.length>0)p+="<"+A.iH(c)+">"
s=a.eC.get(p)
if(s!=null)return s
r=new A.c_(null,null)
r.w=8
r.x=b
r.y=c
if(c.length>0)r.c=c[0]
r.as=p
q=A.dy(a,r)
a.eC.set(p,q)
return q},
z3(a,b,c){var s,r,q,p,o,n
if(b.w===9){s=b.x
r=b.y.concat(c)}else{r=c
s=b}q=s.as+(";<"+A.iH(r)+">")
p=a.eC.get(q)
if(p!=null)return p
o=new A.c_(null,null)
o.w=9
o.x=s
o.y=r
o.as=q
n=A.dy(a,o)
a.eC.set(q,n)
return n},
BB(a,b,c){var s,r,q="+"+(b+"("+A.iH(c)+")"),p=a.eC.get(q)
if(p!=null)return p
s=new A.c_(null,null)
s.w=10
s.x=b
s.y=c
s.as=q
r=A.dy(a,s)
a.eC.set(q,r)
return r},
By(a,b,c){var s,r,q,p,o,n=b.as,m=c.a,l=m.length,k=c.b,j=k.length,i=c.c,h=i.length,g="("+A.iH(m)
if(j>0){s=l>0?",":""
g+=s+"["+A.iH(k)+"]"}if(h>0){s=l>0?",":""
g+=s+"{"+A.FF(i)+"}"}r=n+(g+")")
q=a.eC.get(r)
if(q!=null)return q
p=new A.c_(null,null)
p.w=11
p.x=b
p.y=c
p.as=r
o=A.dy(a,p)
a.eC.set(r,o)
return o},
z4(a,b,c,d){var s,r=b.as+("<"+A.iH(c)+">"),q=a.eC.get(r)
if(q!=null)return q
s=A.FH(a,b,c,r,d)
a.eC.set(r,s)
return s},
FH(a,b,c,d,e){var s,r,q,p,o,n,m,l
if(e){s=c.length
r=A.wZ(s)
for(q=0,p=0;p<s;++p){o=c[p]
if(o.w===1){r[p]=o;++q}}if(q>0){n=A.dB(a,b,r,0)
m=A.fC(a,c,r,0)
return A.z4(a,n,m,c!==m)}}l=new A.c_(null,null)
l.w=12
l.x=b
l.y=c
l.as=d
return A.dy(a,l)},
Bs(a,b,c,d){return{u:a,e:b,r:c,s:[],p:0,n:d}},
Bu(a){var s,r,q,p,o,n,m,l=a.r,k=a.s
for(s=l.length,r=0;r<s;){q=l.charCodeAt(r)
if(q>=48&&q<=57)r=A.Ft(r+1,q,l,k)
else if((((q|32)>>>0)-97&65535)<26||q===95||q===36||q===124)r=A.Bt(a,r,l,k,!1)
else if(q===46)r=A.Bt(a,r,l,k,!0)
else{++r
switch(q){case 44:break
case 58:k.push(!1)
break
case 33:k.push(!0)
break
case 59:k.push(A.eg(a.u,a.e,k.pop()))
break
case 94:k.push(A.FJ(a.u,k.pop()))
break
case 35:k.push(A.iJ(a.u,5,"#"))
break
case 64:k.push(A.iJ(a.u,2,"@"))
break
case 126:k.push(A.iJ(a.u,3,"~"))
break
case 60:k.push(a.p)
a.p=k.length
break
case 62:A.Fv(a,k)
break
case 38:A.Fu(a,k)
break
case 63:p=a.u
k.push(A.BA(p,A.eg(p,a.e,k.pop()),a.n))
break
case 47:p=a.u
k.push(A.Bz(p,A.eg(p,a.e,k.pop()),a.n))
break
case 40:k.push(-3)
k.push(a.p)
a.p=k.length
break
case 41:A.Fs(a,k)
break
case 91:k.push(a.p)
a.p=k.length
break
case 93:o=k.splice(a.p)
A.Bv(a.u,a.e,o)
a.p=k.pop()
k.push(o)
k.push(-1)
break
case 123:k.push(a.p)
a.p=k.length
break
case 125:o=k.splice(a.p)
A.Fx(a.u,a.e,o)
a.p=k.pop()
k.push(o)
k.push(-2)
break
case 43:n=l.indexOf("(",r)
k.push(l.substring(r,n))
k.push(-4)
k.push(a.p)
a.p=k.length
r=n+1
break
default:throw"Bad character "+q}}}m=k.pop()
return A.eg(a.u,a.e,m)},
Ft(a,b,c,d){var s,r,q=b-48
for(s=c.length;a<s;++a){r=c.charCodeAt(a)
if(!(r>=48&&r<=57))break
q=q*10+(r-48)}d.push(q)
return a},
Bt(a,b,c,d,e){var s,r,q,p,o,n,m=b+1
for(s=c.length;m<s;++m){r=c.charCodeAt(m)
if(r===46){if(e)break
e=!0}else{if(!((((r|32)>>>0)-97&65535)<26||r===95||r===36||r===124))q=r>=48&&r<=57
else q=!0
if(!q)break}}p=c.substring(b,m)
if(e){s=a.u
o=a.e
if(o.w===9)o=o.x
n=A.FN(s,o.x)[p]
if(n==null)A.ak('No "'+p+'" in "'+A.EQ(o)+'"')
d.push(A.iK(s,o,n))}else d.push(p)
return m},
Fv(a,b){var s,r=a.u,q=A.Br(a,b),p=b.pop()
if(typeof p=="string")b.push(A.iI(r,p,q))
else{s=A.eg(r,a.e,p)
switch(s.w){case 11:b.push(A.z4(r,s,q,a.n))
break
default:b.push(A.z3(r,s,q))
break}}},
Fs(a,b){var s,r,q,p=a.u,o=b.pop(),n=null,m=null
if(typeof o=="number")switch(o){case-1:n=b.pop()
break
case-2:m=b.pop()
break
default:b.push(o)
break}else b.push(o)
s=A.Br(a,b)
o=b.pop()
switch(o){case-3:o=b.pop()
if(n==null)n=p.sEA
if(m==null)m=p.sEA
r=A.eg(p,a.e,o)
q=new A.mf()
q.a=s
q.b=n
q.c=m
b.push(A.By(p,r,q))
return
case-4:b.push(A.BB(p,b.pop(),s))
return
default:throw A.d(A.jg("Unexpected state under `()`: "+A.w(o)))}},
Fu(a,b){var s=b.pop()
if(0===s){b.push(A.iJ(a.u,1,"0&"))
return}if(1===s){b.push(A.iJ(a.u,4,"1&"))
return}throw A.d(A.jg("Unexpected extended operation "+A.w(s)))},
Br(a,b){var s=b.splice(a.p)
A.Bv(a.u,a.e,s)
a.p=b.pop()
return s},
eg(a,b,c){if(typeof c=="string")return A.iI(a,c,a.sEA)
else if(typeof c=="number"){b.toString
return A.Fw(a,b,c)}else return c},
Bv(a,b,c){var s,r=c.length
for(s=0;s<r;++s)c[s]=A.eg(a,b,c[s])},
Fx(a,b,c){var s,r=c.length
for(s=2;s<r;s+=3)c[s]=A.eg(a,b,c[s])},
Fw(a,b,c){var s,r,q=b.w
if(q===9){if(c===0)return b.x
s=b.y
r=s.length
if(c<=r)return s[c-1]
c-=r
b=b.x
q=b.w}else if(c===0)return b
if(q!==8)throw A.d(A.jg("Indexed base must be an interface type"))
s=b.y
if(c<=s.length)return s[c-1]
throw A.d(A.jg("Bad index "+c+" for "+b.k(0)))},
CG(a,b,c){var s,r=b.d
if(r==null)r=b.d=new Map()
s=r.get(c)
if(s==null){s=A.aP(a,b,null,c,null)
r.set(c,s)}return s},
aP(a,b,c,d,e){var s,r,q,p,o,n,m,l,k,j,i
if(b===d)return!0
if(A.en(d))return!0
s=b.w
if(s===4)return!0
if(A.en(b))return!1
if(b.w===1)return!0
r=s===13
if(r)if(A.aP(a,c[b.x],c,d,e))return!0
q=d.w
p=t.a
if(b===p||b===t.bE){if(q===7)return A.aP(a,b,c,d.x,e)
return d===p||d===t.bE||q===6}if(d===t.K){if(s===7)return A.aP(a,b.x,c,d,e)
return s!==6}if(s===7){if(!A.aP(a,b.x,c,d,e))return!1
return A.aP(a,A.yT(a,b),c,d,e)}if(s===6)return A.aP(a,p,c,d,e)&&A.aP(a,b.x,c,d,e)
if(q===7){if(A.aP(a,b,c,d.x,e))return!0
return A.aP(a,b,c,A.yT(a,d),e)}if(q===6)return A.aP(a,b,c,p,e)||A.aP(a,b,c,d.x,e)
if(r)return!1
p=s!==11
if((!p||s===12)&&d===t.gY)return!0
o=s===10
if(o&&d===t.nJ)return!0
if(q===12){if(b===t.k)return!0
if(s!==12)return!1
n=b.y
m=d.y
l=n.length
if(l!==m.length)return!1
c=c==null?n:n.concat(c)
e=e==null?m:m.concat(e)
for(k=0;k<l;++k){j=n[k]
i=m[k]
if(!A.aP(a,j,c,i,e)||!A.aP(a,i,e,j,c))return!1}return A.C5(a,b.x,c,d.x,e)}if(q===11){if(b===t.k)return!0
if(p)return!1
return A.C5(a,b,c,d,e)}if(s===8){if(q!==8)return!1
return A.Gu(a,b,c,d,e)}if(o&&q===10)return A.Gz(a,b,c,d,e)
return!1},
C5(a3,a4,a5,a6,a7){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e,d,c,b,a,a0,a1,a2
if(!A.aP(a3,a4.x,a5,a6.x,a7))return!1
s=a4.y
r=a6.y
q=s.a
p=r.a
o=q.length
n=p.length
if(o>n)return!1
m=n-o
l=s.b
k=r.b
j=l.length
i=k.length
if(o+j<n+i)return!1
for(h=0;h<o;++h){g=q[h]
if(!A.aP(a3,p[h],a7,g,a5))return!1}for(h=0;h<m;++h){g=l[h]
if(!A.aP(a3,p[o+h],a7,g,a5))return!1}for(h=0;h<i;++h){g=l[m+h]
if(!A.aP(a3,k[h],a7,g,a5))return!1}f=s.c
e=r.c
d=f.length
c=e.length
for(b=0,a=0;a<c;a+=3){a0=e[a]
for(;;){if(b>=d)return!1
a1=f[b]
b+=3
if(a0<a1)return!1
a2=f[b-2]
if(a1<a0){if(a2)return!1
continue}g=e[a+1]
if(a2&&!g)return!1
g=f[b-1]
if(!A.aP(a3,e[a+2],a7,g,a5))return!1
break}}while(b<d){if(f[b+1])return!1
b+=3}return!0},
Gu(a,b,c,d,e){var s,r,q,p,o,n=b.x,m=d.x
while(n!==m){s=a.tR[n]
if(s==null)return!1
if(typeof s=="string"){n=s
continue}r=s[m]
if(r==null)return!1
q=r.length
p=q>0?new Array(q):v.typeUniverse.sEA
for(o=0;o<q;++o)p[o]=A.iK(a,b,r[o])
return A.BT(a,p,null,c,d.y,e)}return A.BT(a,b.y,null,c,d.y,e)},
BT(a,b,c,d,e,f){var s,r=b.length
for(s=0;s<r;++s)if(!A.aP(a,b[s],d,e[s],f))return!1
return!0},
Gz(a,b,c,d,e){var s,r=b.y,q=d.y,p=r.length
if(p!==q.length)return!1
if(b.x!==d.x)return!1
for(s=0;s<p;++s)if(!A.aP(a,r[s],c,q[s],e))return!1
return!0},
fH(a){var s=a.w,r=!0
if(!(a===t.a||a===t.bE))if(!A.en(a))if(s!==6)r=s===7&&A.fH(a.x)
return r},
en(a){var s=a.w
return s===2||s===3||s===4||s===5||a===t.X},
BQ(a,b){var s,r,q=Object.keys(b),p=q.length
for(s=0;s<p;++s){r=q[s]
a[r]=b[r]}},
wZ(a){return a>0?new Array(a):v.typeUniverse.sEA},
c_:function c_(a,b){var _=this
_.a=a
_.b=b
_.r=_.f=_.d=_.c=null
_.w=0
_.as=_.Q=_.z=_.y=_.x=null},
mf:function mf(){this.c=this.b=this.a=null},
mN:function mN(a){this.a=a},
m8:function m8(){},
fu:function fu(a){this.a=a},
Fa(){var s,r,q
if(self.scheduleImmediate!=null)return A.GZ()
if(self.MutationObserver!=null&&self.document!=null){s={}
r=self.document.createElement("div")
q=self.document.createElement("span")
s.a=null
new self.MutationObserver(A.fG(new A.u5(s),1)).observe(r,{childList:true})
return new A.u4(s,r,q)}else if(self.setImmediate!=null)return A.H_()
return A.H0()},
Fb(a){self.scheduleImmediate(A.fG(new A.u6(t.M.a(a)),0))},
Fc(a){self.setImmediate(A.fG(new A.u7(t.M.a(a)),0))},
Fd(a){A.yV(B.Z,t.M.a(a))},
yV(a,b){var s=B.c.ag(a.a,1000)
return A.FD(s<0?0:s,b)},
FD(a,b){var s=new A.mL()
s.j6(a,b)
return s},
Q(a){return new A.lT(new A.a_($.a0,a.h("a_<0>")),a.h("lT<0>"))},
P(a,b){a.$2(0,null)
b.b=!0
return b.a},
G(a,b){A.G2(a,b)},
O(a,b){b.ba(a)},
N(a,b){b.df(A.a1(a),A.b3(a))},
G2(a,b){var s,r,q=new A.xa(b),p=new A.xb(b)
if(a instanceof A.a_)a.hm(q,p,t.z)
else{s=t.z
if(t.g7.b(a))a.b_(q,p,s)
else{r=new A.a_($.a0,t.j_)
r.a=8
r.c=a
r.hm(q,p,s)}}},
R(a){var s=function(b,c){return function(d,e){while(true){try{b(d,e)
break}catch(r){e=r
d=c}}}}(a,1)
return $.a0.dC(new A.xr(s),t.H,t.S,t.z)},
Bx(a,b,c){return 0},
ys(a){var s
if(t.B.b(a)){s=a.gbh()
if(s!=null)return s}return B.M},
pq(a,b){var s=a==null?b.a(a):a,r=new A.a_($.a0,b.h("a_<0>"))
r.bj(s)
return r},
yC(a,b){var s
if(!b.b(null))throw A.d(A.dH(null,"computation","The type parameter is not nullable"))
s=new A.a_($.a0,b.h("a_<0>"))
A.t8(a,new A.pp(null,s,b))
return s},
DX(a,b,c,d){var s,r,q,p=new A.pn(d,null,b,c)
if(a instanceof A.a_){c.h("a_<0>").a(a)
c.h("0/(u,ba)").a(p)
s=$.a0
r=new A.a_(s,c.h("a_<0>"))
q=s!==B.m?s.dC(p,c.h("0/"),t.K,t.l):p
a.bz(new A.bE(r,2,null,q,a.$ti.h("@<1>").A(c).h("bE<1,2>")))
return r}return a.b_(new A.pm(c),p,c)},
DY(a,b){var s,r,q,p=A.a([],b.h("D<ig<0>>"))
for(s=a.length,r=b.h("ig<0>"),q=0;q<a.length;a.length===s||(0,A.I)(a),++q)p.push(new A.ig(a[q],r))
if(p.length===0)return A.pq(A.a([],b.h("D<0>")),b.h("q<0>"))
s=new A.a_($.a0,b.h("a_<q<0>>"))
A.Fi(p,new A.po(new A.iE(s,b.h("iE<q<0>>")),p,b))
return s},
GG(a){return a!=null},
Fi(a,b){var s,r={},q=r.a=r.b=0,p=new A.uP(r,a,b)
for(s=a.length;q<a.length;a.length===s||(0,A.I)(a),++q)a[q].lF(p)},
Gq(a,b){if($.a0===B.m)return null
return null},
zb(a,b){if($.a0!==B.m)A.Gq(a,b)
if(b==null)if(t.B.b(a)){b=a.gbh()
if(b==null){A.yQ(a,B.M)
b=B.M}}else b=B.M
else if(t.B.b(a))A.yQ(a,b)
return new A.aG(a,b)},
Bn(a,b){var s=new A.a_($.a0,b.h("a_<0>"))
b.a(a)
s.a=8
s.c=a
return s},
uV(a,b,c){var s,r,q,p,o={},n=o.a=a
for(s=t.j_;r=n.a,(r&4)!==0;n=a){a=s.a(n.c)
o.a=a}if(n===b){s=A.B6()
b.c1(new A.aG(new A.bI(!0,n,null,"Cannot complete a future with itself"),s))
return}q=b.a&1
s=n.a=r|q
if((s&24)===0){p=t.np.a(b.c)
b.a=b.a&1|4
b.c=n
n.ha(p)
return}if(!c)if(b.c==null)n=(s&16)===0||q!==0
else n=!1
else n=!0
if(n){p=b.cb()
b.cO(o.a)
A.ea(b,p)
return}b.a^=2
A.fB(null,null,b.b,t.M.a(new A.uW(o,b)))},
ea(a,a0){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e,d,c={},b=c.a=a
for(s=t.n,r=t.np,q=t.g7;;){p={}
o=b.a
n=(o&16)===0
m=!n
if(a0==null){if(m&&(o&1)===0){l=s.a(b.c)
A.fA(l.a,l.b)}return}p.a=a0
k=a0.a
for(b=a0;k!=null;b=k,k=j){b.a=null
A.ea(c.a,b)
p.a=k
j=k.a}o=c.a
i=o.c
p.b=m
p.c=i
if(n){h=b.c
h=(h&1)!==0||(h&15)===8}else h=!0
if(h){g=b.b.b
if(m){o=o.b===g
o=!(o||o)}else o=!1
if(o){s.a(i)
A.fA(i.a,i.b)
return}f=$.a0
if(f!==g)$.a0=g
else f=null
b=b.c
if((b&15)===8)new A.v2(p,c,m).$0()
else if(n){if((b&1)!==0)new A.v1(p,i).$0()}else if((b&2)!==0)new A.v0(c,p).$0()
if(f!=null)$.a0=f
b=p.c
if(q.b(b)){o=p.a.$ti
o=o.h("ae<2>").b(b)||!o.y[1].b(b)}else o=!1
if(o){e=p.a.b
if(b instanceof A.a_)if((b.a&24)!==0){d=r.a(e.c)
e.c=null
a0=e.cX(d)
e.a=b.a&30|e.a&1
e.c=b.c
c.a=b
continue}else A.uV(b,e,!0)
else e.e4(b)
return}}e=p.a.b
d=r.a(e.c)
e.c=null
a0=e.cX(d)
b=p.b
o=p.c
if(!b){e.$ti.c.a(o)
e.a=8
e.c=o}else{s.a(o)
e.a=e.a&1|16
e.c=o}c.a=e
b=e}},
Cb(a,b){var s
if(t.ng.b(a))return b.dC(a,t.z,t.K,t.l)
s=t.mq
if(s.b(a))return s.a(a)
throw A.d(A.dH(a,"onError",u.c))},
GF(){var s,r
for(s=$.fy;s!=null;s=$.fy){$.iS=null
r=s.b
$.fy=r
if(r==null)$.iR=null
s.a.$0()}},
GR(){$.zc=!0
try{A.GF()}finally{$.iS=null
$.zc=!1
if($.fy!=null)$.zv().$1(A.Cp())}},
Ci(a){var s=new A.lU(a),r=$.iR
if(r==null){$.fy=$.iR=s
if(!$.zc)$.zv().$1(A.Cp())}else $.iR=r.b=s},
GN(a){var s,r,q,p=$.fy
if(p==null){A.Ci(a)
$.iS=$.iR
return}s=new A.lU(a)
r=$.iS
if(r==null){s.b=p
$.fy=$.iS=s}else{q=r.b
s.b=q
$.iS=r.b=s
if(q==null)$.iR=s}},
yi(a){var s=null,r=$.a0
if(B.m===r){A.fB(s,s,B.m,a)
return}A.fB(s,s,r,t.M.a(r.eJ(a)))},
Iz(a,b){A.fF(a,"stream",t.K)
return new A.mG(b.h("mG<0>"))},
rZ(a){return new A.i3(null,null,a.h("i3<0>"))},
mW(a){var s,r,q
if(a==null)return
try{a.$0()}catch(q){s=A.a1(q)
r=A.b3(q)
A.fA(A.az(s),t.l.a(r))}},
Fh(a,b,c,d,e,f){var s,r,q=$.a0,p=e?1:0,o=c!=null?32:0
t.bm.A(f).h("1(2)").a(b)
s=A.Bj(q,c)
r=d==null?A.Co():d
return new A.d1(a,b,s,t.M.a(r),q,p|o,f.h("d1<0>"))},
Bj(a,b){if(b==null)b=A.H2()
if(t.b9.b(b))return a.dC(b,t.z,t.K,t.l)
if(t.i6.b(b))return t.mq.a(b)
throw A.d(A.ai("handleError callback must take either an Object (the error), or both an Object (the error) and a StackTrace.",null))},
GI(a,b){A.fA(A.az(a),t.l.a(b))},
GH(){},
Bl(a,b){var s=new A.fl($.a0,b.h("fl<0>"))
A.yi(s.gku())
if(a!=null)s.c=t.M.a(a)
return s},
t8(a,b){var s=$.a0
if(s===B.m)return A.yV(a,t.M.a(b))
return A.yV(a,t.M.a(s.eJ(b)))},
fA(a,b){A.GN(new A.xo(a,b))},
Cd(a,b,c,d,e){var s,r=$.a0
if(r===c)return d.$0()
$.a0=c
s=r
try{r=d.$0()
return r}finally{$.a0=s}},
Cf(a,b,c,d,e,f,g){var s,r=$.a0
if(r===c)return d.$1(e)
$.a0=c
s=r
try{r=d.$1(e)
return r}finally{$.a0=s}},
Ce(a,b,c,d,e,f,g,h,i){var s,r=$.a0
if(r===c)return d.$2(e,f)
$.a0=c
s=r
try{r=d.$2(e,f)
return r}finally{$.a0=s}},
fB(a,b,c,d){t.M.a(d)
if(B.m!==c){d=c.eJ(d)
d=d}A.Ci(d)},
u5:function u5(a){this.a=a},
u4:function u4(a,b,c){this.a=a
this.b=b
this.c=c},
u6:function u6(a){this.a=a},
u7:function u7(a){this.a=a},
mL:function mL(){this.b=null},
wI:function wI(a,b){this.a=a
this.b=b},
lT:function lT(a,b){this.a=a
this.b=!1
this.$ti=b},
xa:function xa(a){this.a=a},
xb:function xb(a){this.a=a},
xr:function xr(a){this.a=a},
d5:function d5(a,b){var _=this
_.a=a
_.e=_.d=_.c=_.b=null
_.$ti=b},
d4:function d4(a,b){this.a=a
this.$ti=b},
aG:function aG(a,b){this.a=a
this.b=b},
aM:function aM(a,b){this.a=a
this.$ti=b},
d0:function d0(a,b,c,d,e,f,g){var _=this
_.ay=0
_.CW=_.ch=null
_.w=a
_.a=b
_.b=c
_.c=d
_.d=e
_.e=f
_.r=_.f=null
_.$ti=g},
i5:function i5(){},
i3:function i3(a,b,c){var _=this
_.a=a
_.b=b
_.c=0
_.r=_.e=_.d=null
_.$ti=c},
pp:function pp(a,b,c){this.a=a
this.b=b
this.c=c},
pn:function pn(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.d=d},
pm:function pm(a){this.a=a},
e5:function e5(a,b){this.a=a
this.b=b},
po:function po(a,b,c){this.a=a
this.b=b
this.c=c},
hF:function hF(a,b,c){this.c=a
this.d=b
this.$ti=c},
ig:function ig(a,b){var _=this
_.a=a
_.c=_.b=null
_.$ti=b},
uQ:function uQ(a,b){this.a=a
this.b=b},
uR:function uR(a,b){this.a=a
this.b=b},
uP:function uP(a,b,c){this.a=a
this.b=b
this.c=c},
fk:function fk(){},
c3:function c3(a,b){this.a=a
this.$ti=b},
iE:function iE(a,b){this.a=a
this.$ti=b},
bE:function bE(a,b,c,d,e){var _=this
_.a=null
_.b=a
_.c=b
_.d=c
_.e=d
_.$ti=e},
a_:function a_(a,b){var _=this
_.a=0
_.b=a
_.c=null
_.$ti=b},
uS:function uS(a,b){this.a=a
this.b=b},
v_:function v_(a,b){this.a=a
this.b=b},
uX:function uX(a){this.a=a},
uY:function uY(a){this.a=a},
uZ:function uZ(a,b,c){this.a=a
this.b=b
this.c=c},
uW:function uW(a,b){this.a=a
this.b=b},
uU:function uU(a,b){this.a=a
this.b=b},
uT:function uT(a,b){this.a=a
this.b=b},
v2:function v2(a,b,c){this.a=a
this.b=b
this.c=c},
v3:function v3(a,b){this.a=a
this.b=b},
v4:function v4(a){this.a=a},
v1:function v1(a,b){this.a=a
this.b=b},
v0:function v0(a,b){this.a=a
this.b=b},
v5:function v5(a,b){this.a=a
this.b=b},
v6:function v6(a,b,c){this.a=a
this.b=b
this.c=c},
v7:function v7(a,b){this.a=a
this.b=b},
lU:function lU(a){this.a=a
this.b=null},
aH:function aH(){},
t_:function t_(a,b){this.a=a
this.b=b},
t0:function t0(a,b){this.a=a
this.b=b},
e2:function e2(){},
fr:function fr(){},
wH:function wH(a){this.a=a},
wG:function wG(a){this.a=a},
i4:function i4(){},
dt:function dt(a,b,c,d,e){var _=this
_.a=null
_.b=0
_.c=null
_.d=a
_.e=b
_.f=c
_.r=d
_.$ti=e},
dv:function dv(a,b){this.a=a
this.$ti=b},
d1:function d1(a,b,c,d,e,f,g){var _=this
_.w=a
_.a=b
_.b=c
_.c=d
_.d=e
_.e=f
_.r=_.f=null
_.$ti=g},
fj:function fj(){},
ua:function ua(a,b,c){this.a=a
this.b=b
this.c=c},
u9:function u9(a){this.a=a},
fs:function fs(){},
d2:function d2(){},
c4:function c4(a,b){this.b=a
this.a=null
this.$ti=b},
i9:function i9(a,b){this.b=a
this.c=b
this.a=null},
m0:function m0(){},
c6:function c6(a){var _=this
_.a=0
_.c=_.b=null
_.$ti=a},
vR:function vR(a,b){this.a=a
this.b=b},
fl:function fl(a,b){var _=this
_.a=1
_.b=a
_.c=null
_.$ti=b},
mG:function mG(a){this.$ti=a},
ib:function ib(a){this.$ti=a},
il:function il(a,b){this.b=a
this.$ti=b},
vE:function vE(a,b){this.a=a
this.b=b},
im:function im(a,b,c,d,e){var _=this
_.a=null
_.b=0
_.c=null
_.d=a
_.e=b
_.f=c
_.r=d
_.$ti=e},
iP:function iP(){},
mC:function mC(){},
vU:function vU(a,b){this.a=a
this.b=b},
vV:function vV(a,b,c){this.a=a
this.b=b
this.c=c},
xo:function xo(a,b){this.a=a
this.b=b},
yE(a,b){return new A.eb(a.h("@<0>").A(b).h("eb<1,2>"))},
Bo(a,b){var s=a[b]
return s===a?null:s},
z0(a,b,c){if(c==null)a[b]=a
else a[b]=c},
z_(){var s=Object.create(null)
A.z0(s,"<non-identifier-key>",s)
delete s["<non-identifier-key>"]
return s},
ke(a,b,c,d){if(b==null){if(a==null)return new A.bz(c.h("@<0>").A(d).h("bz<1,2>"))
b=A.H9()}else{if(A.Hf()===b&&A.He()===a)return new A.hs(c.h("@<0>").A(d).h("hs<1,2>"))
if(a==null)a=A.H8()}return A.Fq(a,b,null,c,d)},
j(a,b,c){return b.h("@<0>").A(c).h("qc<1,2>").a(A.Hn(a,new A.bz(b.h("@<0>").A(c).h("bz<1,2>"))))},
t(a,b){return new A.bz(a.h("@<0>").A(b).h("bz<1,2>"))},
Fq(a,b,c,d,e){return new A.ik(a,b,new A.vr(d),d.h("@<0>").A(e).h("ik<1,2>"))},
eI(a){return new A.ed(a.h("ed<0>"))},
z1(){var s=Object.create(null)
s["<non-identifier-key>"]=s
delete s["<non-identifier-key>"]
return s},
yN(a){return new A.c5(a.h("c5<0>"))},
cJ(a){return new A.c5(a.h("c5<0>"))},
Eo(a,b){return b.h("AA<0>").a(A.Ho(a,new A.c5(b.h("c5<0>"))))},
z2(){var s=Object.create(null)
s["<non-identifier-key>"]=s
delete s["<non-identifier-key>"]
return s},
Bq(a,b,c){var s=new A.ef(a,b,c.h("ef<0>"))
s.c=a.e
return s},
Ga(a,b){return J.a8(a,b)},
Gb(a){return J.Z(a)},
As(a,b,c){var s=A.yE(b,c)
s.B(0,a)
return s},
yH(a,b){var s=J.aE(a)
if(s.p())return s.gu()
return null},
qe(a,b,c){var s=A.ke(null,null,b,c)
a.aa(0,new A.qf(s,b,c))
return s},
ce(a,b,c){var s=A.ke(null,null,b,c)
s.B(0,a)
return s},
Ep(a,b){var s,r=A.yN(b)
for(s=J.aE(a);s.p();)r.m(0,b.a(s.gu()))
return r},
yO(a,b){var s=A.yN(b)
s.B(0,a)
return s},
Eq(a,b){var s=t.bP
return J.yn(s.a(a),s.a(b))},
qq(a){var s,r
if(A.zn(a))return"{...}"
s=new A.aI("")
try{r={}
B.b.m($.bG,a)
s.a+="{"
r.a=!0
a.aa(0,new A.qr(r,s))
s.a+="}"}finally{if(0>=$.bG.length)return A.f($.bG,-1)
$.bG.pop()}r=s.a
return r.charCodeAt(0)==0?r:r},
eb:function eb(a){var _=this
_.a=0
_.e=_.d=_.c=_.b=null
_.$ti=a},
vc:function vc(a){this.a=a},
fo:function fo(a){var _=this
_.a=0
_.e=_.d=_.c=_.b=null
_.$ti=a},
ii:function ii(a,b){this.a=a
this.$ti=b},
ec:function ec(a,b,c){var _=this
_.a=a
_.b=b
_.c=0
_.d=null
_.$ti=c},
ik:function ik(a,b,c,d){var _=this
_.w=a
_.x=b
_.y=c
_.a=0
_.f=_.e=_.d=_.c=_.b=null
_.r=0
_.$ti=d},
vr:function vr(a){this.a=a},
ed:function ed(a){var _=this
_.a=0
_.e=_.d=_.c=_.b=null
_.$ti=a},
d3:function d3(a,b,c){var _=this
_.a=a
_.b=b
_.c=0
_.d=null
_.$ti=c},
c5:function c5(a){var _=this
_.a=0
_.f=_.e=_.d=_.c=_.b=null
_.r=0
_.$ti=a},
mq:function mq(a){this.a=a
this.c=this.b=null},
ef:function ef(a,b,c){var _=this
_.a=a
_.b=b
_.d=_.c=null
_.$ti=c},
qf:function qf(a,b,c){this.a=a
this.b=b
this.c=c},
T:function T(){},
a5:function a5(){},
qp:function qp(a){this.a=a},
qr:function qr(a,b){this.a=a
this.b=b},
iL:function iL(){},
eU:function eU(){},
d_:function d_(a,b){this.a=a
this.$ti=b},
cR:function cR(){},
iz:function iz(){},
fw:function fw(){},
GJ(a,b){var s,r,q,p=null
try{p=JSON.parse(a)}catch(r){s=A.a1(r)
q=A.ap(String(s),null,null)
throw A.d(q)}q=A.xf(p)
return q},
xf(a){var s
if(a==null)return null
if(typeof a!="object")return a
if(!Array.isArray(a))return new A.mo(a,Object.create(null))
for(s=0;s<a.length;++s)a[s]=A.xf(a[s])
return a},
FY(a,b,c){var s,r,q,p,o=c-b
if(o<=4096)s=$.D8()
else s=new Uint8Array(o)
for(r=J.aT(a),q=0;q<o;++q){p=r.j(a,b+q)
if((p&255)!==p)p=255
s[q]=p}return s},
FX(a,b,c,d){var s=a?$.D7():$.D6()
if(s==null)return null
if(0===c&&d===b.length)return A.BP(s,b)
return A.BP(s,b.subarray(c,d))},
BP(a,b){var s,r
try{s=a.decode(b)
return s}catch(r){}return null},
Ab(a,b,c,d,e,f){if(B.c.bX(f,4)!==0)throw A.d(A.ap("Invalid base64 padding, padded length must be multiple of four, is "+f,a,c))
if(d+e!==f)throw A.d(A.ap("Invalid base64 padding, '=' not at the end",a,b))
if(e>2)throw A.d(A.ap("Invalid base64 padding, more than two '=' characters",a,b))},
Fg(a,b,c,d,a0,a1){var s,r,q,p,o,n,m,l,k,j,i="Invalid encoding before padding",h="Invalid character",g=B.c.bm(a1,2),f=a1&3,e=$.zw()
for(s=a.length,r=e.length,q=d.$flags|0,p=b,o=0;p<c;++p){if(!(p<s))return A.f(a,p)
n=a.charCodeAt(p)
o|=n
m=n&127
if(!(m<r))return A.f(e,m)
l=e[m]
if(l>=0){g=(g<<6|l)&16777215
f=f+1&3
if(f===0){k=a0+1
q&2&&A.au(d)
m=d.length
if(!(a0<m))return A.f(d,a0)
d[a0]=g>>>16&255
a0=k+1
if(!(k<m))return A.f(d,k)
d[k]=g>>>8&255
k=a0+1
if(!(a0<m))return A.f(d,a0)
d[a0]=g&255
a0=k
g=0}continue}else if(l===-1&&f>1){if(o>127)break
if(f===3){if((g&3)!==0)throw A.d(A.ap(i,a,p))
k=a0+1
q&2&&A.au(d)
s=d.length
if(!(a0<s))return A.f(d,a0)
d[a0]=g>>>10
if(!(k<s))return A.f(d,k)
d[k]=g>>>2}else{if((g&15)!==0)throw A.d(A.ap(i,a,p))
q&2&&A.au(d)
if(!(a0<d.length))return A.f(d,a0)
d[a0]=g>>>4}j=(3-f)*3
if(n===37)j+=2
return A.Bi(a,p+1,c,-j-1)}throw A.d(A.ap(h,a,p))}if(o>=0&&o<=127)return(g<<2|f)>>>0
for(p=b;p<c;++p){if(!(p<s))return A.f(a,p)
if(a.charCodeAt(p)>127)break}throw A.d(A.ap(h,a,p))},
Fe(a,b,c,d){var s=A.Ff(a,b,c),r=(d&3)+(s-b),q=B.c.bm(r,2)*3,p=r&3
if(p!==0&&s<c)q+=p-1
if(q>0)return new Uint8Array(q)
return $.D4()},
Ff(a,b,c){var s,r=a.length,q=c,p=q,o=0
for(;;){if(!(p>b&&o<2))break
A:{--p
if(!(p>=0&&p<r))return A.f(a,p)
s=a.charCodeAt(p)
if(s===61){++o
q=p
break A}if((s|32)===100){if(p===b)break;--p
if(!(p>=0&&p<r))return A.f(a,p)
s=a.charCodeAt(p)}if(s===51){if(p===b)break;--p
if(!(p>=0&&p<r))return A.f(a,p)
s=a.charCodeAt(p)}if(s===37){++o
q=p
break A}break}}return q},
Bi(a,b,c,d){var s,r,q
if(b===c)return d
s=-d-1
for(r=a.length;s>0;){if(!(b<r))return A.f(a,b)
q=a.charCodeAt(b)
if(s===3){if(q===61){s-=3;++b
break}if(q===37){--s;++b
if(b===c)break
if(!(b<r))return A.f(a,b)
q=a.charCodeAt(b)}else break}if((s>3?s-3:s)===2){if(q!==51)break;++b;--s
if(b===c)break
if(!(b<r))return A.f(a,b)
q=a.charCodeAt(b)}if((q|32)!==100)break;++b;--s
if(b===c)break}if(b!==c)throw A.d(A.ap("Invalid padding character",a,b))
return-s-1},
Aq(a){return B.f2.j(0,a.toLowerCase())},
Aw(a,b,c){return new A.ht(a,b)},
Gc(a){return a.a0()},
Fo(a,b){return new A.vo(a,[],A.Hb())},
Fp(a,b,c){var s,r=new A.aI(""),q=A.Fo(r,b)
q.dM(a)
s=r.a
return s.charCodeAt(0)==0?s:s},
FZ(a){switch(a){case 65:return"Missing extension byte"
case 67:return"Unexpected extension byte"
case 69:return"Invalid UTF-8 byte"
case 71:return"Overlong encoding"
case 73:return"Out of unicode range"
case 75:return"Encoded surrogate"
case 77:return"Unfinished UTF-8 octet sequence"
default:return""}},
mo:function mo(a,b){this.a=a
this.b=b
this.c=null},
mp:function mp(a){this.a=a},
wX:function wX(){},
wW:function wW(){},
je:function je(){},
wR:function wR(){},
nU:function nU(a){this.a=a},
wQ:function wQ(){},
nT:function nT(a,b){this.a=a
this.b=b},
fR:function fR(a){this.a=a},
jj:function jj(a){this.a=a},
nY:function nY(){},
u8:function u8(){this.a=0},
o6:function o6(){},
lW:function lW(a,b){this.a=a
this.b=b
this.c=0},
cx:function cx(){},
jF:function jF(){},
df:function df(){},
ht:function ht(a,b){this.a=a
this.b=b},
kc:function kc(a,b){this.a=a
this.b=b},
kb:function kb(){},
q2:function q2(a){this.b=a},
q1:function q1(a){this.a=a},
vp:function vp(){},
vq:function vq(a,b){this.a=a
this.b=b},
vo:function vo(a,b,c){this.c=a
this.a=b
this.b=c},
kd:function kd(){},
qa:function qa(a){this.a=a},
q9:function q9(a,b){this.a=a
this.b=b},
lF:function lF(){},
to:function to(){},
wY:function wY(a){this.b=0
this.c=a},
tn:function tn(a){this.a=a},
wV:function wV(a){this.a=a
this.b=16
this.c=0},
HU(a){return A.eo(a)},
CE(a,b){var s=A.hH(a,b)
if(s!=null)return s
throw A.d(A.ap(a,null,null))},
DQ(a,b){a=A.aD(a,new Error())
if(a==null)a=A.az(a)
a.stack=b.k(0)
throw a},
bL(a,b,c,d){var s,r=c?J.Eg(a,d):J.yJ(a,d)
if(a!==0&&b!=null)for(s=0;s<r.length;++s)r[s]=b
return r},
qg(a,b,c){var s,r=A.a([],c.h("D<0>"))
for(s=J.aE(a);s.p();)B.b.m(r,c.a(s.gu()))
if(b)return r
r.$flags=1
return r},
x(a,b){var s,r
if(Array.isArray(a))return A.a(a.slice(0),b.h("D<0>"))
s=A.a([],b.h("D<0>"))
for(r=J.aE(a);r.p();)B.b.m(s,r.gu())
return s},
al(a,b){var s=A.qg(a,!1,b)
s.$flags=3
return s},
hW(a,b,c){var s,r
A.bo(b,"start")
s=c!=null
if(s){r=c-b
if(r<0)throw A.d(A.an(c,b,null,"end",null))
if(r===0)return""}if(t.hD.b(a))return A.F_(a,b,c)
if(s)a=A.e4(a,0,A.fF(c,"count",t.S),A.aX(a).h("T.E"))
if(b>0)a=J.ns(a,b)
s=A.x(a,t.S)
return A.EC(s)},
F_(a,b,c){var s=a.length
if(b>=s)return""
return A.EE(a,b,c==null||c>s?s:c)},
ar(a,b){return new A.dP(a,A.yK(a,!1,b,!1,!1,""))},
HT(a,b){return a==null?b==null:a===b},
yU(a,b,c){var s=J.aE(b)
if(!s.p())return a
if(c.length===0){do a+=A.w(s.gu())
while(s.p())}else{a+=A.w(s.gu())
while(s.p())a=a+c+A.w(s.gu())}return a},
yY(){var s,r,q=A.EA()
if(q==null)throw A.d(A.ao("'Uri.base' is not supported"))
s=$.Bd
if(s!=null&&q===$.Bc)return s
r=A.bN(q)
$.Bd=r
$.Bc=q
return r},
mO(a,b,c,d){var s,r,q,p,o,n="0123456789ABCDEF"
if(c===B.l){s=$.D5()
s=s.b.test(b)}else s=!1
if(s)return b
r=c.di(b)
for(s=r.length,q=0,p="";q<s;++q){o=r[q]
if(o<128&&(u.v.charCodeAt(o)&a)!==0)p+=A.am(o)
else p=d&&o===32?p+"+":p+"%"+n[o>>>4&15]+n[o&15]}return p.charCodeAt(0)==0?p:p},
B6(){return A.b3(new Error())},
DI(a,b,c){var s="microsecond"
if(b>999)throw A.d(A.an(b,0,999,s,null))
if(a<-864e13||a>864e13)throw A.d(A.an(a,-864e13,864e13,"millisecondsSinceEpoch",null))
if(a===864e13&&b!==0)throw A.d(A.dH(b,s,"Time including microseconds is outside valid range"))
A.fF(!0,"isUtc",t.k4)
return a},
Am(a){var s=Math.abs(a),r=a<0?"-":""
if(s>=1000)return""+a
if(s>=100)return r+"0"+s
if(s>=10)return r+"00"+s
return r+"000"+s},
DH(a){var s=Math.abs(a),r=a<0?"-":"+"
if(s>=1e5)return r+s
return r+"0"+s},
oI(a){if(a>=100)return""+a
if(a>=10)return"0"+a
return"00"+a},
cz(a){if(a>=10)return""+a
return"0"+a},
Ap(a,b){return new A.ca(a+1000*b)},
jN(a){if(typeof a=="number"||A.xk(a)||a==null)return J.aF(a)
if(typeof a=="string")return JSON.stringify(a)
return A.AT(a)},
Ar(a,b){A.fF(a,"error",t.K)
A.fF(b,"stackTrace",t.l)
A.DQ(a,b)},
jg(a){return new A.jf(a)},
ai(a,b){return new A.bI(!1,null,b,a)},
dH(a,b,c){return new A.bI(!0,a,b,c)},
nS(a,b,c){return a},
b1(a){var s=null
return new A.f_(s,s,!1,s,s,a)},
qL(a,b){return new A.f_(null,null,!0,a,b,"Value not in range")},
an(a,b,c,d,e){return new A.f_(b,c,!0,a,d,"Invalid value")},
yR(a,b,c,d){if(a<b||a>c)throw A.d(A.an(a,b,c,d,null))
return a},
ch(a,b,c){if(0>a||a>c)throw A.d(A.an(a,0,c,"start",null))
if(b!=null){if(a>b||b>c)throw A.d(A.an(b,a,c,"end",null))
return b}return c},
bo(a,b){if(a<0)throw A.d(A.an(a,0,null,b,null))
return a},
pW(a,b,c,d){return new A.k2(b,!0,a,d,"Index out of range")},
ao(a){return new A.hY(a)},
yW(a){return new A.lA(a)},
cU(a){return new A.ck(a)},
aB(a){return new A.jy(a)},
DS(a){return new A.dw(a)},
ap(a,b,c){return new A.bn(a,b,c)},
Ef(a,b,c){var s,r
if(A.zn(a)){if(b==="("&&c===")")return"(...)"
return b+"..."+c}s=A.a([],t.s)
B.b.m($.bG,a)
try{A.GD(a,s)}finally{if(0>=$.bG.length)return A.f($.bG,-1)
$.bG.pop()}r=A.yU(b,t.e7.a(s),", ")+c
return r.charCodeAt(0)==0?r:r},
yI(a,b,c){var s,r
if(A.zn(a))return b+"..."+c
s=new A.aI(b)
B.b.m($.bG,a)
try{r=s
r.a=A.yU(r.a,a,", ")}finally{if(0>=$.bG.length)return A.f($.bG,-1)
$.bG.pop()}s.a+=c
r=s.a
return r.charCodeAt(0)==0?r:r},
GD(a,b){var s,r,q,p,o,n,m,l=a.gC(a),k=0,j=0
for(;;){if(!(k<80||j<3))break
if(!l.p())return
s=A.w(l.gu())
B.b.m(b,s)
k+=s.length+2;++j}if(!l.p()){if(j<=5)return
if(0>=b.length)return A.f(b,-1)
r=b.pop()
if(0>=b.length)return A.f(b,-1)
q=b.pop()}else{p=l.gu();++j
if(!l.p()){if(j<=4){B.b.m(b,A.w(p))
return}r=A.w(p)
if(0>=b.length)return A.f(b,-1)
q=b.pop()
k+=r.length+2}else{o=l.gu();++j
for(;l.p();p=o,o=n){n=l.gu();++j
if(j>100){for(;;){if(!(k>75&&j>3))break
if(0>=b.length)return A.f(b,-1)
k-=b.pop().length+2;--j}B.b.m(b,"...")
return}}q=A.w(p)
r=A.w(o)
k+=r.length+q.length+4}}if(j>b.length+2){k+=5
m="..."}else m=null
for(;;){if(!(k>80&&b.length>3))break
if(0>=b.length)return A.f(b,-1)
k-=b.pop().length+2
if(m==null){k+=5
m="..."}}if(m!=null)B.b.m(b,m)
B.b.m(b,q)
B.b.m(b,r)},
cL(a,b,c,d,e,f,g,h,i,j){var s
if(B.d===c){s=J.Z(a)
b=J.Z(b)
return A.cV(A.V(A.V($.cp(),s),b))}if(B.d===d){s=J.Z(a)
b=J.Z(b)
c=J.Z(c)
return A.cV(A.V(A.V(A.V($.cp(),s),b),c))}if(B.d===e){s=J.Z(a)
b=J.Z(b)
c=J.Z(c)
d=J.Z(d)
return A.cV(A.V(A.V(A.V(A.V($.cp(),s),b),c),d))}if(B.d===f){s=J.Z(a)
b=J.Z(b)
c=J.Z(c)
d=J.Z(d)
e=J.Z(e)
return A.cV(A.V(A.V(A.V(A.V(A.V($.cp(),s),b),c),d),e))}if(B.d===g){s=J.Z(a)
b=J.Z(b)
c=J.Z(c)
d=J.Z(d)
e=J.Z(e)
f=A.b0(f)
return A.cV(A.V(A.V(A.V(A.V(A.V(A.V($.cp(),s),b),c),d),e),f))}if(B.d===h){s=J.Z(a)
b=J.Z(b)
c=J.Z(c)
d=J.Z(d)
e=J.Z(e)
f=A.b0(f)
g=A.b0(g)
return A.cV(A.V(A.V(A.V(A.V(A.V(A.V(A.V($.cp(),s),b),c),d),e),f),g))}if(B.d===i){s=J.Z(a)
b=J.Z(b)
c=J.Z(c)
d=J.Z(d)
e=J.Z(e)
f=A.b0(f)
g=A.b0(g)
h=A.b0(h)
return A.cV(A.V(A.V(A.V(A.V(A.V(A.V(A.V(A.V($.cp(),s),b),c),d),e),f),g),h))}if(B.d===j){s=J.Z(a)
b=J.Z(b)
c=J.Z(c)
d=J.Z(d)
e=J.Z(e)
f=A.b0(f)
g=A.b0(g)
h=A.b0(h)
i=J.Z(i)
return A.cV(A.V(A.V(A.V(A.V(A.V(A.V(A.V(A.V(A.V($.cp(),s),b),c),d),e),f),g),h),i))}s=J.Z(a)
b=J.Z(b)
c=J.Z(c)
d=J.Z(d)
e=J.Z(e)
f=A.b0(f)
g=A.b0(g)
h=A.b0(h)
i=J.Z(i)
j=J.Z(j)
j=A.cV(A.V(A.V(A.V(A.V(A.V(A.V(A.V(A.V(A.V(A.V($.cp(),s),b),c),d),e),f),g),h),i),j))
return j},
AF(a){var s,r,q=$.cp()
for(s=a.length,r=0;r<a.length;a.length===s||(0,A.I)(a),++r)q=A.V(q,J.Z(a[r]))
return A.cV(q)},
bN(a5){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e,d,c,b,a,a0,a1,a2,a3=null,a4=a5.length
if(a4>=5){if(4>=a4)return A.f(a5,4)
s=((a5.charCodeAt(4)^58)*3|a5.charCodeAt(0)^100|a5.charCodeAt(1)^97|a5.charCodeAt(2)^116|a5.charCodeAt(3)^97)>>>0
if(s===0)return A.Bb(a4<a4?B.a.q(a5,0,a4):a5,5,a3).gig()
else if(s===32)return A.Bb(B.a.q(a5,5,a4),0,a3).gig()}r=A.bL(8,0,!1,t.S)
B.b.i(r,0,0)
B.b.i(r,1,-1)
B.b.i(r,2,-1)
B.b.i(r,7,-1)
B.b.i(r,3,0)
B.b.i(r,4,0)
B.b.i(r,5,a4)
B.b.i(r,6,a4)
if(A.Ch(a5,0,a4,0,r)>=14)B.b.i(r,7,a4)
q=r[1]
if(q>=0)if(A.Ch(a5,0,q,20,r)===20)r[7]=q
p=r[2]+1
o=r[3]
n=r[4]
m=r[5]
l=r[6]
if(l<m)m=l
if(n<p)n=m
else if(n<=q)n=q+1
if(o<p)o=n
k=r[7]<0
j=a3
if(k){k=!1
if(!(p>q+3)){i=o>0
if(!(i&&o+1===n)){if(!B.a.V(a5,"\\",n))if(p>0)h=B.a.V(a5,"\\",p-1)||B.a.V(a5,"\\",p-2)
else h=!1
else h=!0
if(!h){if(!(m<a4&&m===n+2&&B.a.V(a5,"..",n)))h=m>n+2&&B.a.V(a5,"/..",m-3)
else h=!0
if(!h)if(q===4){if(B.a.V(a5,"file",0)){if(p<=0){if(!B.a.V(a5,"/",n)){g="file:///"
s=3}else{g="file://"
s=2}a5=g+B.a.q(a5,n,a4)
m+=s
l+=s
a4=a5.length
p=7
o=7
n=7}else if(n===m){++l
f=m+1
a5=B.a.bf(a5,n,m,"/");++a4
m=f}j="file"}else if(B.a.V(a5,"http",0)){if(i&&o+3===n&&B.a.V(a5,"80",o+1)){l-=3
e=n-3
m-=3
a5=B.a.bf(a5,o,n,"")
a4-=3
n=e}j="http"}}else if(q===5&&B.a.V(a5,"https",0)){if(i&&o+4===n&&B.a.V(a5,"443",o+1)){l-=4
e=n-4
m-=4
a5=B.a.bf(a5,o,n,"")
a4-=3
n=e}j="https"}k=!h}}}}if(k)return new A.bR(a4<a5.length?B.a.q(a5,0,a4):a5,q,p,o,n,m,l,j)
if(j==null)if(q>0)j=A.z6(a5,0,q)
else{if(q===0)A.fx(a5,0,"Invalid empty scheme")
j=""}d=a3
if(p>0){c=q+3
b=c<p?A.BK(a5,c,p-1):""
a=A.BH(a5,p,o,!1)
i=o+1
if(i<n){a0=A.hH(B.a.q(a5,i,n),a3)
d=A.wT(a0==null?A.ak(A.ap("Invalid port",a5,i)):a0,j)}}else{a=a3
b=""}a1=A.BI(a5,n,m,a3,j,a!=null)
a2=m<l?A.BJ(a5,m+1,l,a3):a3
return A.iN(j,b,a,d,a1,a2,l<a4?A.BG(a5,l+1,a4):a3)},
F7(a){A.r(a)
return A.d6(a,0,a.length,B.l,!1)},
Bf(a){var s=t.N
return B.b.eQ(A.a(a.split("&"),t.s),A.t(s,s),new A.tm(B.l),t.f)},
lD(a,b,c){throw A.d(A.ap("Illegal IPv4 address, "+a,b,c))},
F4(a,b,c,d,e){var s,r,q,p,o,n,m,l,k,j="invalid character"
for(s=a.length,r=b,q=r,p=0,o=0;;){if(q>=c)n=0
else{if(!(q>=0&&q<s))return A.f(a,q)
n=a.charCodeAt(q)}m=n^48
if(m<=9){if(o!==0||q===r){o=o*10+m
if(o<=255){++q
continue}A.lD("each part must be in the range 0..255",a,r)}A.lD("parts must not have leading zeros",a,r)}if(q===r){if(q===c)break
A.lD(j,a,q)}l=p+1
k=e+p
d.$flags&2&&A.au(d)
if(!(k<16))return A.f(d,k)
d[k]=o
if(n===46){if(l<4){++q
p=l
r=q
o=0
continue}break}if(q===c){if(l===4)return
break}A.lD(j,a,q)
p=l}A.lD("IPv4 address should contain exactly 4 parts",a,q)},
F5(a,b,c){var s
if(b===c)throw A.d(A.ap("Empty IP address",a,b))
if(!(b>=0&&b<a.length))return A.f(a,b)
if(a.charCodeAt(b)===118){s=A.F6(a,b,c)
if(s!=null)throw A.d(s)
return!1}A.Be(a,b,c)
return!0},
F6(a,b,c){var s,r,q,p,o,n="Missing hex-digit in IPvFuture address",m=u.v;++b
for(s=a.length,r=b;;r=q){if(r<c){q=r+1
if(!(r>=0&&r<s))return A.f(a,r)
p=a.charCodeAt(r)
if((p^48)<=9)continue
o=p|32
if(o>=97&&o<=102)continue
if(p===46){if(q-1===b)return new A.bn(n,a,q)
r=q
break}return new A.bn("Unexpected character",a,q-1)}if(r-1===b)return new A.bn(n,a,r)
return new A.bn("Missing '.' in IPvFuture address",a,r)}if(r===c)return new A.bn("Missing address in IPvFuture address, host, cursor",null,null)
for(;;){if(!(r>=0&&r<s))return A.f(a,r)
p=a.charCodeAt(r)
if(!(p<128))return A.f(m,p)
if((m.charCodeAt(p)&16)!==0){++r
if(r<c)continue
return null}return new A.bn("Invalid IPvFuture address character",a,r)}},
Be(a3,a4,a5){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e,d,c,b,a,a0,a1="an address must contain at most 8 parts",a2=new A.tl(a3)
if(a5-a4<2)a2.$2("address is too short",null)
s=new Uint8Array(16)
r=a3.length
if(!(a4>=0&&a4<r))return A.f(a3,a4)
q=-1
p=0
if(a3.charCodeAt(a4)===58){o=a4+1
if(!(o<r))return A.f(a3,o)
if(a3.charCodeAt(o)===58){n=a4+2
m=n
q=0
p=1}else{a2.$2("invalid start colon",a4)
n=a4
m=n}}else{n=a4
m=n}for(l=0,k=!0;;){if(n>=a5)j=0
else{if(!(n<r))return A.f(a3,n)
j=a3.charCodeAt(n)}A:{i=j^48
h=!1
if(i<=9)g=i
else{f=j|32
if(f>=97&&f<=102)g=f-87
else break A
k=h}if(n<m+4){l=l*16+g;++n
continue}a2.$2("an IPv6 part can contain a maximum of 4 hex digits",m)}if(n>m){if(j===46){if(k){if(p<=6){A.F4(a3,m,a5,s,p*2)
p+=2
n=a5
break}a2.$2(a1,m)}break}o=p*2
e=B.c.bm(l,8)
if(!(o<16))return A.f(s,o)
s[o]=e;++o
if(!(o<16))return A.f(s,o)
s[o]=l&255;++p
if(j===58){if(p<8){++n
m=n
l=0
k=!0
continue}a2.$2(a1,n)}break}if(j===58){if(q<0){d=p+1;++n
q=p
p=d
m=n
continue}a2.$2("only one wildcard `::` is allowed",n)}if(q!==p-1)a2.$2("missing part",n)
break}if(n<a5)a2.$2("invalid character",n)
if(p<8){if(q<0)a2.$2("an address without a wildcard must contain exactly 8 parts",a5)
c=q+1
b=p-c
if(b>0){a=c*2
a0=16-b*2
B.G.bg(s,a0,16,s,a)
B.G.mt(s,a,a0,0)}}return s},
iN(a,b,c,d,e,f,g){return new A.iM(a,b,c,d,e,f,g)},
BD(a){if(a==="http")return 80
if(a==="https")return 443
return 0},
fx(a,b,c){throw A.d(A.ap(c,a,b))},
FP(a,b){var s,r,q
for(s=a.length,r=0;r<s;++r){q=a[r]
if(B.a.v(q,"/")){s=A.ao("Illegal path character "+q)
throw A.d(s)}}},
FR(a){var s
if(a.length===0)return B.b1
s=A.BO(a)
s.ic(A.Ct())
return A.yy(s,t.N,t.h)},
wT(a,b){if(a!=null&&a===A.BD(b))return null
return a},
BH(a,b,c,d){var s,r,q,p,o,n,m,l,k
if(a==null)return null
if(b===c)return""
s=a.length
if(!(b>=0&&b<s))return A.f(a,b)
if(a.charCodeAt(b)===91){r=c-1
if(!(r>=0&&r<s))return A.f(a,r)
if(a.charCodeAt(r)!==93)A.fx(a,b,"Missing end `]` to match `[` in host")
q=b+1
if(!(q<s))return A.f(a,q)
p=""
if(a.charCodeAt(q)!==118){o=A.FQ(a,q,r)
if(o<r){n=o+1
p=A.BN(a,B.a.V(a,"25",n)?o+3:n,r,"%25")}}else o=r
m=A.F5(a,q,o)
l=B.a.q(a,q,o)
return"["+(m?l.toLowerCase():l)+p+"]"}for(k=b;k<c;++k){if(!(k<s))return A.f(a,k)
if(a.charCodeAt(k)===58){o=B.a.aV(a,"%",b)
o=o>=b&&o<c?o:c
if(o<c){n=o+1
p=A.BN(a,B.a.V(a,"25",n)?o+3:n,c,"%25")}else p=""
A.Be(a,b,o)
return"["+B.a.q(a,b,o)+p+"]"}}return A.FV(a,b,c)},
FQ(a,b,c){var s=B.a.aV(a,"%",b)
return s>=b&&s<c?s:c},
BN(a,b,c,d){var s,r,q,p,o,n,m,l,k,j,i,h=d!==""?new A.aI(d):null
for(s=a.length,r=b,q=r,p=!0;r<c;){if(!(r>=0&&r<s))return A.f(a,r)
o=a.charCodeAt(r)
if(o===37){n=A.z7(a,r,!0)
m=n==null
if(m&&p){r+=3
continue}if(h==null)h=new A.aI("")
l=h.a+=B.a.q(a,q,r)
if(m)n=B.a.q(a,r,r+3)
else if(n==="%")A.fx(a,r,"ZoneID should not contain % anymore")
h.a=l+n
r+=3
q=r
p=!0}else if(o<127&&(u.v.charCodeAt(o)&1)!==0){if(p&&65<=o&&90>=o){if(h==null)h=new A.aI("")
if(q<r){h.a+=B.a.q(a,q,r)
q=r}p=!1}++r}else{k=1
if((o&64512)===55296&&r+1<c){m=r+1
if(!(m<s))return A.f(a,m)
j=a.charCodeAt(m)
if((j&64512)===56320){o=65536+((o&1023)<<10)+(j&1023)
k=2}}i=B.a.q(a,q,r)
if(h==null){h=new A.aI("")
m=h}else m=h
m.a+=i
l=A.z5(o)
m.a+=l
r+=k
q=r}}if(h==null)return B.a.q(a,b,c)
if(q<c){i=B.a.q(a,q,c)
h.a+=i}s=h.a
return s.charCodeAt(0)==0?s:s},
FV(a,b,c){var s,r,q,p,o,n,m,l,k,j,i,h,g=u.v
for(s=a.length,r=b,q=r,p=null,o=!0;r<c;){if(!(r>=0&&r<s))return A.f(a,r)
n=a.charCodeAt(r)
if(n===37){m=A.z7(a,r,!0)
l=m==null
if(l&&o){r+=3
continue}if(p==null)p=new A.aI("")
k=B.a.q(a,q,r)
if(!o)k=k.toLowerCase()
j=p.a+=k
i=3
if(l)m=B.a.q(a,r,r+3)
else if(m==="%"){m="%25"
i=1}p.a=j+m
r+=i
q=r
o=!0}else if(n<127&&(g.charCodeAt(n)&32)!==0){if(o&&65<=n&&90>=n){if(p==null)p=new A.aI("")
if(q<r){p.a+=B.a.q(a,q,r)
q=r}o=!1}++r}else if(n<=93&&(g.charCodeAt(n)&1024)!==0)A.fx(a,r,"Invalid character")
else{i=1
if((n&64512)===55296&&r+1<c){l=r+1
if(!(l<s))return A.f(a,l)
h=a.charCodeAt(l)
if((h&64512)===56320){n=65536+((n&1023)<<10)+(h&1023)
i=2}}k=B.a.q(a,q,r)
if(!o)k=k.toLowerCase()
if(p==null){p=new A.aI("")
l=p}else l=p
l.a+=k
j=A.z5(n)
l.a+=j
r+=i
q=r}}if(p==null)return B.a.q(a,b,c)
if(q<c){k=B.a.q(a,q,c)
if(!o)k=k.toLowerCase()
p.a+=k}s=p.a
return s.charCodeAt(0)==0?s:s},
z6(a,b,c){var s,r,q,p
if(b===c)return""
s=a.length
if(!(b<s))return A.f(a,b)
if(!A.BF(a.charCodeAt(b)))A.fx(a,b,"Scheme not starting with alphabetic character")
for(r=b,q=!1;r<c;++r){if(!(r<s))return A.f(a,r)
p=a.charCodeAt(r)
if(!(p<128&&(u.v.charCodeAt(p)&8)!==0))A.fx(a,r,"Illegal scheme character")
if(65<=p&&p<=90)q=!0}a=B.a.q(a,b,c)
return A.FO(q?a.toLowerCase():a)},
FO(a){if(a==="http")return"http"
if(a==="file")return"file"
if(a==="https")return"https"
if(a==="package")return"package"
return a},
BK(a,b,c){if(a==null)return""
return A.iO(a,b,c,16,!1,!1)},
BI(a,b,c,d,e,f){var s,r=e==="file",q=r||f
if(a==null)return r?"/":""
else s=A.iO(a,b,c,128,!0,!0)
if(s.length===0){if(r)return"/"}else if(q&&!B.a.M(s,"/"))s="/"+s
return A.FU(s,e,f)},
FU(a,b,c){var s=b.length===0
if(s&&!c&&!B.a.M(a,"/")&&!B.a.M(a,"\\"))return A.z8(a,!s||c)
return A.ek(a)},
BJ(a,b,c,d){if(a!=null)return A.iO(a,b,c,256,!0,!1)
return null},
BG(a,b,c){if(a==null)return null
return A.iO(a,b,c,256,!0,!1)},
z7(a,b,c){var s,r,q,p,o,n,m=u.v,l=b+2,k=a.length
if(l>=k)return"%"
s=b+1
if(!(s>=0&&s<k))return A.f(a,s)
r=a.charCodeAt(s)
if(!(l>=0))return A.f(a,l)
q=a.charCodeAt(l)
p=A.y1(r)
o=A.y1(q)
if(p<0||o<0)return"%"
n=p*16+o
if(n<127){if(!(n>=0))return A.f(m,n)
l=(m.charCodeAt(n)&1)!==0}else l=!1
if(l)return A.am(c&&65<=n&&90>=n?(n|32)>>>0:n)
if(r>=97||q>=97)return B.a.q(a,b,b+3).toUpperCase()
return null},
z5(a){var s,r,q,p,o,n,m,l,k="0123456789ABCDEF"
if(a<=127){s=new Uint8Array(3)
s[0]=37
r=a>>>4
if(!(r<16))return A.f(k,r)
s[1]=k.charCodeAt(r)
s[2]=k.charCodeAt(a&15)}else{if(a>2047)if(a>65535){q=240
p=4}else{q=224
p=3}else{q=192
p=2}r=3*p
s=new Uint8Array(r)
for(o=0;--p,p>=0;q=128){n=B.c.lj(a,6*p)&63|q
if(!(o<r))return A.f(s,o)
s[o]=37
m=o+1
l=n>>>4
if(!(l<16))return A.f(k,l)
if(!(m<r))return A.f(s,m)
s[m]=k.charCodeAt(l)
l=o+2
if(!(l<r))return A.f(s,l)
s[l]=k.charCodeAt(n&15)
o+=3}}return A.hW(s,0,null)},
iO(a,b,c,d,e,f){var s=A.BM(a,b,c,d,e,f)
return s==null?B.a.q(a,b,c):s},
BM(a,b,c,d,e,f){var s,r,q,p,o,n,m,l,k,j,i=null,h=u.v
for(s=!e,r=a.length,q=b,p=q,o=i;q<c;){if(!(q>=0&&q<r))return A.f(a,q)
n=a.charCodeAt(q)
if(n<127&&(h.charCodeAt(n)&d)!==0)++q
else{m=1
if(n===37){l=A.z7(a,q,!1)
if(l==null){q+=3
continue}if("%"===l)l="%25"
else m=3}else if(n===92&&f)l="/"
else if(s&&n<=93&&(h.charCodeAt(n)&1024)!==0){A.fx(a,q,"Invalid character")
m=i
l=m}else{if((n&64512)===55296){k=q+1
if(k<c){if(!(k<r))return A.f(a,k)
j=a.charCodeAt(k)
if((j&64512)===56320){n=65536+((n&1023)<<10)+(j&1023)
m=2}}}l=A.z5(n)}if(o==null){o=new A.aI("")
k=o}else k=o
k.a=(k.a+=B.a.q(a,p,q))+l
if(typeof m!=="number")return A.zk(m)
q+=m
p=q}}if(o==null)return i
if(p<c){s=B.a.q(a,p,c)
o.a+=s}s=o.a
return s.charCodeAt(0)==0?s:s},
BL(a){if(B.a.M(a,"."))return!0
return B.a.aU(a,"/.")!==-1},
ek(a){var s,r,q,p,o,n,m
if(!A.BL(a))return a
s=A.a([],t.s)
for(r=a.split("/"),q=r.length,p=!1,o=0;o<q;++o){n=r[o]
if(n===".."){m=s.length
if(m!==0){if(0>=m)return A.f(s,-1)
s.pop()
if(s.length===0)B.b.m(s,"")}p=!0}else{p="."===n
if(!p)B.b.m(s,n)}}if(p)B.b.m(s,"")
return B.b.aA(s,"/")},
z8(a,b){var s,r,q,p,o,n
if(!A.BL(a))return!b?A.BE(a):a
s=A.a([],t.s)
for(r=a.split("/"),q=r.length,p=!1,o=0;o<q;++o){n=r[o]
if(".."===n){if(s.length!==0&&B.b.gaL(s)!==".."){if(0>=s.length)return A.f(s,-1)
s.pop()}else B.b.m(s,"..")
p=!0}else{p="."===n
if(!p)B.b.m(s,n.length===0&&s.length===0?"./":n)}}if(s.length===0)return"./"
if(p)B.b.m(s,"")
if(!b){if(0>=s.length)return A.f(s,0)
B.b.i(s,0,A.BE(s[0]))}return B.b.aA(s,"/")},
BE(a){var s,r,q,p=u.v,o=a.length
if(o>=2&&A.BF(a.charCodeAt(0)))for(s=1;s<o;++s){r=a.charCodeAt(s)
if(r===58)return B.a.q(a,0,s)+"%3A"+B.a.S(a,s+1)
if(r<=127){if(!(r<128))return A.f(p,r)
q=(p.charCodeAt(r)&8)===0}else q=!0
if(q)break}return a},
FW(a,b){if(a.mF("package")&&a.c==null)return A.Cj(b,0,b.length)
return-1},
FS(){return A.a([],t.s)},
BO(a){var s,r,q,p,o,n=A.t(t.N,t.h),m=new A.wU(a,B.l,n)
for(s=a.length,r=0,q=0,p=-1;r<s;){o=a.charCodeAt(r)
if(o===61){if(p<0)p=r}else if(o===38){m.$3(q,p,r)
q=r+1
p=-1}++r}m.$3(q,p,r)
return n},
FT(a,b){var s,r,q,p,o
for(s=a.length,r=0,q=0;q<2;++q){p=b+q
if(!(p>=0&&p<s))return A.f(a,p)
o=a.charCodeAt(p)
if(48<=o&&o<=57)r=r*16+o-48
else{o|=32
if(97<=o&&o<=102)r=r*16+o-87
else throw A.d(A.ai("Invalid URL encoding",null))}}return r},
d6(a,b,c,d,e){var s,r,q,p,o=a.length,n=b
for(;;){if(!(n<c)){s=!0
break}if(!(n>=0&&n<o))return A.f(a,n)
r=a.charCodeAt(n)
q=!0
if(r<=127)if(r!==37)q=e&&r===43
if(q){s=!1
break}++n}if(s)if(B.l===d)return B.a.q(a,b,c)
else p=new A.c9(B.a.q(a,b,c))
else{p=A.a([],t.lC)
for(n=b;n<c;++n){if(!(n>=0&&n<o))return A.f(a,n)
r=a.charCodeAt(n)
if(r>127)throw A.d(A.ai("Illegal percent encoding in URI",null))
if(r===37){if(n+3>o)throw A.d(A.ai("Truncated URI",null))
B.b.m(p,A.FT(a,n+1))
n+=2}else if(e&&r===43)B.b.m(p,32)
else B.b.m(p,r)}}return d.a7(p)},
BF(a){var s=a|32
return 97<=s&&s<=122},
Bb(a,b,c){var s,r,q,p,o,n,m,l,k="Invalid MIME type",j=A.a([b-1],t.lC)
for(s=a.length,r=b,q=-1,p=null;r<s;++r){p=a.charCodeAt(r)
if(p===44||p===59)break
if(p===47){if(q<0){q=r
continue}throw A.d(A.ap(k,a,r))}}if(q<0&&r>b)throw A.d(A.ap(k,a,r))
while(p!==44){B.b.m(j,r);++r
for(o=-1;r<s;++r){if(!(r>=0))return A.f(a,r)
p=a.charCodeAt(r)
if(p===61){if(o<0)o=r}else if(p===59||p===44)break}if(o>=0)B.b.m(j,o)
else{n=B.b.gaL(j)
if(p!==44||r!==n+7||!B.a.V(a,"base64",n+1))throw A.d(A.ap("Expecting '='",a,r))
break}}B.b.m(j,r)
m=r+1
if((j.length&1)===1)a=B.c8.hX(a,m,s)
else{l=A.BM(a,m,s,256,!0,!1)
if(l!=null)a=B.a.bf(a,m,s,l)}return new A.tk(a,j,c)},
Ch(a,b,c,d,e){var s,r,q,p,o,n='\xe1\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\xe1\xe1\xe1\x01\xe1\xe1\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\xe1\xe3\xe1\xe1\x01\xe1\x01\xe1\xcd\x01\xe1\x01\x01\x01\x01\x01\x01\x01\x01\x0e\x03\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01"\x01\xe1\x01\xe1\xac\xe1\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\xe1\xe1\xe1\x01\xe1\xe1\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\xe1\xea\xe1\xe1\x01\xe1\x01\xe1\xcd\x01\xe1\x01\x01\x01\x01\x01\x01\x01\x01\x01\n\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01"\x01\xe1\x01\xe1\xac\xeb\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\xeb\xeb\xeb\x8b\xeb\xeb\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\xeb\x83\xeb\xeb\x8b\xeb\x8b\xeb\xcd\x8b\xeb\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x92\x83\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\x8b\xeb\x8b\xeb\x8b\xeb\xac\xeb\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\xeb\xeb\xeb\v\xeb\xeb\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\xebD\xeb\xeb\v\xeb\v\xeb\xcd\v\xeb\v\v\v\v\v\v\v\v\x12D\v\v\v\v\v\v\v\v\v\v\xeb\v\xeb\v\xeb\xac\xe5\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\xe5\xe5\xe5\x05\xe5D\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe8\x8a\xe5\xe5\x05\xe5\x05\xe5\xcd\x05\xe5\x05\x05\x05\x05\x05\x05\x05\x05\x05\x8a\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05f\x05\xe5\x05\xe5\xac\xe5\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05\xe5\xe5\xe5\x05\xe5D\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\xe5\x8a\xe5\xe5\x05\xe5\x05\xe5\xcd\x05\xe5\x05\x05\x05\x05\x05\x05\x05\x05\x05\x8a\x05\x05\x05\x05\x05\x05\x05\x05\x05\x05f\x05\xe5\x05\xe5\xac\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7D\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\x8a\xe7\xe7\xe7\xe7\xe7\xe7\xcd\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\x8a\xe7\x07\x07\x07\x07\x07\x07\x07\x07\x07\xe7\xe7\xe7\xe7\xe7\xac\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7D\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\x8a\xe7\xe7\xe7\xe7\xe7\xe7\xcd\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\xe7\x8a\x07\x07\x07\x07\x07\x07\x07\x07\x07\x07\xe7\xe7\xe7\xe7\xe7\xac\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\x05\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\xeb\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\xeb\xeb\xeb\v\xeb\xeb\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\xeb\xea\xeb\xeb\v\xeb\v\xeb\xcd\v\xeb\v\v\v\v\v\v\v\v\x10\xea\v\v\v\v\v\v\v\v\v\v\xeb\v\xeb\v\xeb\xac\xeb\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\xeb\xeb\xeb\v\xeb\xeb\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\xeb\xea\xeb\xeb\v\xeb\v\xeb\xcd\v\xeb\v\v\v\v\v\v\v\v\x12\n\v\v\v\v\v\v\v\v\v\v\xeb\v\xeb\v\xeb\xac\xeb\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\xeb\xeb\xeb\v\xeb\xeb\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\xeb\xea\xeb\xeb\v\xeb\v\xeb\xcd\v\xeb\v\v\v\v\v\v\v\v\v\n\v\v\v\v\v\v\v\v\v\v\xeb\v\xeb\v\xeb\xac\xec\f\f\f\f\f\f\f\f\f\f\f\f\f\f\f\f\f\f\f\f\f\f\f\f\f\f\xec\xec\xec\f\xec\xec\f\f\f\f\f\f\f\f\f\f\f\f\f\f\f\f\f\f\f\f\f\f\f\f\f\f\xec\xec\xec\xec\f\xec\f\xec\xcd\f\xec\f\f\f\f\f\f\f\f\f\xec\f\f\f\f\f\f\f\f\f\f\xec\f\xec\f\xec\f\xed\r\r\r\r\r\r\r\r\r\r\r\r\r\r\r\r\r\r\r\r\r\r\r\r\r\r\xed\xed\xed\r\xed\xed\r\r\r\r\r\r\r\r\r\r\r\r\r\r\r\r\r\r\r\r\r\r\r\r\r\r\xed\xed\xed\xed\r\xed\r\xed\xed\r\xed\r\r\r\r\r\r\r\r\r\xed\r\r\r\r\r\r\r\r\r\r\xed\r\xed\r\xed\r\xe1\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\xe1\xe1\xe1\x01\xe1\xe1\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\xe1\xea\xe1\xe1\x01\xe1\x01\xe1\xcd\x01\xe1\x01\x01\x01\x01\x01\x01\x01\x01\x0f\xea\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01"\x01\xe1\x01\xe1\xac\xe1\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\xe1\xe1\xe1\x01\xe1\xe1\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01\xe1\xe9\xe1\xe1\x01\xe1\x01\xe1\xcd\x01\xe1\x01\x01\x01\x01\x01\x01\x01\x01\x01\t\x01\x01\x01\x01\x01\x01\x01\x01\x01\x01"\x01\xe1\x01\xe1\xac\xeb\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\xeb\xeb\xeb\v\xeb\xeb\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\xeb\xea\xeb\xeb\v\xeb\v\xeb\xcd\v\xeb\v\v\v\v\v\v\v\v\x11\xea\v\v\v\v\v\v\v\v\v\v\xeb\v\xeb\v\xeb\xac\xeb\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\xeb\xeb\xeb\v\xeb\xeb\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\xeb\xe9\xeb\xeb\v\xeb\v\xeb\xcd\v\xeb\v\v\v\v\v\v\v\v\v\t\v\v\v\v\v\v\v\v\v\v\xeb\v\xeb\v\xeb\xac\xeb\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\xeb\xeb\xeb\v\xeb\xeb\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\xeb\xea\xeb\xeb\v\xeb\v\xeb\xcd\v\xeb\v\v\v\v\v\v\v\v\x13\xea\v\v\v\v\v\v\v\v\v\v\xeb\v\xeb\v\xeb\xac\xeb\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\xeb\xeb\xeb\v\xeb\xeb\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\v\xeb\xea\xeb\xeb\v\xeb\v\xeb\xcd\v\xeb\v\v\v\v\v\v\v\v\v\xea\v\v\v\v\v\v\v\v\v\v\xeb\v\xeb\v\xeb\xac\xf5\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\xf5\x15\xf5\x15\x15\xf5\x15\x15\x15\x15\x15\x15\x15\x15\x15\x15\xf5\xf5\xf5\xf5\xf5\xf5'
for(s=a.length,r=b;r<c;++r){if(!(r<s))return A.f(a,r)
q=a.charCodeAt(r)^96
if(q>95)q=31
p=d*96+q
if(!(p<2112))return A.f(n,p)
o=n.charCodeAt(p)
d=o&31
B.b.i(e,o>>>5,r)}return d},
Bw(a){if(a.b===7&&B.a.M(a.a,"package")&&a.c<=0)return A.Cj(a.a,a.e,a.f)
return-1},
GV(a,b){A.r(a)
return A.al(t.h.a(b),t.N)},
Cj(a,b,c){var s,r,q,p
for(s=a.length,r=b,q=0;r<c;++r){if(!(r>=0&&r<s))return A.f(a,r)
p=a.charCodeAt(r)
if(p===47)return q!==0?r:-1
if(p===37||p===58)return-1
q|=p^46}return-1},
G9(a,b,c){var s,r,q,p,o,n,m,l
for(s=a.length,r=b.length,q=0,p=0;p<s;++p){o=c+p
if(!(o<r))return A.f(b,o)
n=b.charCodeAt(o)
m=a.charCodeAt(p)^n
if(m!==0){if(m===32){l=n|m
if(97<=l&&l<=122){q=32
continue}}return-1}}return q},
b6:function b6(a,b,c){this.a=a
this.b=b
this.c=c},
ca:function ca(a){this.a=a},
us:function us(){},
ad:function ad(){},
jf:function jf(a){this.a=a},
cY:function cY(){},
bI:function bI(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.d=d},
f_:function f_(a,b,c,d,e,f){var _=this
_.e=a
_.f=b
_.a=c
_.b=d
_.c=e
_.d=f},
k2:function k2(a,b,c,d,e){var _=this
_.f=a
_.a=b
_.b=c
_.c=d
_.d=e},
hY:function hY(a){this.a=a},
lA:function lA(a){this.a=a},
ck:function ck(a){this.a=a},
jy:function jy(a){this.a=a},
kw:function kw(){},
hS:function hS(){},
dw:function dw(a){this.a=a},
bn:function bn(a,b,c){this.a=a
this.b=b
this.c=c},
m:function m(){},
W:function W(a,b,c){this.a=a
this.b=b
this.$ti=c},
aa:function aa(){},
u:function u(){},
mJ:function mJ(){},
aI:function aI(a){this.a=a},
tm:function tm(a){this.a=a},
tl:function tl(a){this.a=a},
iM:function iM(a,b,c,d,e,f,g){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=g
_.Q=_.z=_.y=_.x=_.w=$},
wU:function wU(a,b,c){this.a=a
this.b=b
this.c=c},
tk:function tk(a,b,c){this.a=a
this.b=b
this.c=c},
bR:function bR(a,b,c,d,e,f,g,h){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=g
_.w=h
_.x=null},
m_:function m_(a,b,c,d,e,f,g){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=g
_.Q=_.z=_.y=_.x=_.w=$},
jQ:function jQ(a,b,c){this.a=a
this.b=b
this.$ti=c},
kt:function kt(a){this.a=a},
dA(a){var s
if(typeof a=="function")throw A.d(A.ai("Attempting to rewrap a JS function.",null))
s=function(b,c){return function(d){return b(c,d,arguments.length)}}(A.G7,a)
s[$.yk()]=a
return s},
G7(a,b,c){t.gY.a(a)
if(A.bb(c)>=1)return a.$1(b)
return a.$0()},
G8(a,b,c,d,e){t.gY.a(a)
A.bb(e)
if(e>=3)return a.$3(b,c,d)
if(e===2)return a.$2(b,c)
if(e===1)return a.$1(b)
return a.$0()},
C9(a){return a==null||A.xk(a)||typeof a=="number"||typeof a=="string"||t.jx.b(a)||t.ev.b(a)||t.nn.b(a)||t.m6.b(a)||t.hM.b(a)||t.bW.b(a)||t.mC.b(a)||t.pk.b(a)||t.kI.b(a)||t.lo.b(a)||t.fW.b(a)},
zo(a){if(A.C9(a))return a
return new A.y6(new A.fo(t.mp)).$1(a)},
y0(a,b,c){return c.a(a[b])},
nk(a,b){var s=new A.a_($.a0,b.h("a_<0>")),r=new A.c3(s,b.h("c3<0>"))
a.then(A.fG(new A.yc(r,b),1),A.fG(new A.yd(r),1))
return s},
C8(a){return a==null||typeof a==="boolean"||typeof a==="number"||typeof a==="string"||a instanceof Int8Array||a instanceof Uint8Array||a instanceof Uint8ClampedArray||a instanceof Int16Array||a instanceof Uint16Array||a instanceof Int32Array||a instanceof Uint32Array||a instanceof Float32Array||a instanceof Float64Array||a instanceof ArrayBuffer||a instanceof DataView},
xU(a){if(A.C8(a))return a
return new A.xV(new A.fo(t.mp)).$1(a)},
y6:function y6(a){this.a=a},
yc:function yc(a,b){this.a=a
this.b=b},
yd:function yd(a){this.a=a},
xV:function xV(a){this.a=a},
ez(a,b){var s=null
return new A.fW(a,B.ct,s,s,s,!0,s)},
jp(a,b,c,d){return new A.fW(b,B.cu,d,a,null,!0,null)},
fW:function fW(a,b,c,d,e,f,g){var _=this
_.d=a
_.f=b
_.r=c
_.w=d
_.x=e
_.z=f
_.a=g},
bJ(a,b){return new A.h_(a,b,null)},
h_:function h_(a,b,c){this.d=a
this.e=b
this.a=c},
zG(a,b,c,d,e,f){return new A.fM(f,c,a,e,d,!0,null)},
fM:function fM(a,b,c,d,e,f,g){var _=this
_.d=a
_.e=b
_.f=c
_.w=d
_.x=e
_.y=f
_.a=g},
dc(a,b){var s,r,q=$.bD
if(q==null)q=$.bD=new A.cX(A.a([],t.I),A.a([],t.u),B.y)
s=Date.now()
r=q.c
return q.ey(new A.cn("toast_"+s,a,null,b,B.jW,4000,null,null,r))},
cu(a,b){var s,r,q=$.bD
if(q==null)q=$.bD=new A.cX(A.a([],t.I),A.a([],t.u),B.y)
s=Date.now()
r=q.c
return q.ey(new A.cn("toast_"+s,a,null,b,B.an,6000,null,null,r))},
jb:function jb(a){this.a=a},
ft:function ft(a,b,c,d,e){var _=this
_.d=a
_.e=b
_.f=c
_.r=d
_.a=e},
iG:function iG(){this.c=this.a=null},
wL:function wL(){},
wM:function wM(){},
cn:function cn(a,b,c,d,e,f,g,h,i){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.w=g
_.x=h
_.y=i},
cX:function cX(a,b,c){this.a=a
this.b=b
this.c=c},
A9(a,b){return new A.fP(a,b,null)},
fP:function fP(a,b,c){this.d=a
this.e=b
this.a=c},
A7(a,b){return new A.aY(a,B.o,B.T,b,!0,null)},
nQ(a){return new A.aY(a,B.o,B.U,!1,!0,null)},
nP(a){return new A.aY(a,B.o,B.am,!1,!0,null)},
yr(a){return new A.aY(a,B.o,B.a2,!1,!0,null)},
A6(a){return new A.aY(a,B.aa,B.a2,!1,!1,null)},
aY:function aY(a,b,c,d,e,f){var _=this
_.d=a
_.f=b
_.r=c
_.x=d
_.y=e
_.a=f},
Ag(a,b,c,d){return new A.cv(b,c,B.aC,d,a,!1,null)},
o4(a,b,c,d){return new A.cv(b,c,B.aC,d,a,!1,null)},
bf(a,b,c,d,e){return new A.cv(c,d,B.cc,e,a,b,null)},
yu(a,b,c,d){return new A.cv(b,c,B.cd,d,a,!1,null)},
yt(a,b,c,d){return new A.cv(b,c,B.aE,d,a,!1,null)},
cv:function cv(a,b,c,d,e,f,g){var _=this
_.d=a
_.w=b
_.y=c
_.z=d
_.Q=e
_.at=f
_.a=g},
j3:function j3(a,b,c,d){var _=this
_.e=a
_.f=b
_.as=c
_.a=d},
nL(a,b,c){return new A.j5(b,c,a,null)},
j5:function j5(a,b,c,d){var _=this
_.d=a
_.e=b
_.ax=c
_.a=d},
nM:function nM(){},
cW(a,b,c,d,e,f,g,h,i,j){return new A.lu(h,i,a,j,b,d,e,f==null?null:f,g,c,null)},
yq(a,b,c,d,e,f,g,h,i,j,k,l){var s=g==null?null:g
return new A.j9(h,l,i,k,a,!1,f,d,e,b,s==null?null:s,!1,null)},
lu:function lu(a,b,c,d,e,f,g,h,i,j,k){var _=this
_.d=a
_.e=b
_.r=c
_.y=d
_.ax=e
_.ay=f
_.ch=g
_.CW=h
_.db=i
_.go=j
_.a=k},
j9:function j9(a,b,c,d,e,f,g,h,i,j,k,l,m){var _=this
_.d=a
_.e=b
_.f=c
_.r=d
_.w=e
_.x=f
_.y=g
_.z=h
_.Q=i
_.as=j
_.at=k
_.ax=l
_.a=m},
nO:function nO(a){this.a=a},
ah:function ah(a,b,c){this.a=a
this.b=b
this.c=c},
nR(a,b,c,d){return new A.jd(d,c==null?null:c,a,b,null)},
jd:function jd(a,b,c,d,e){var _=this
_.e=a
_.f=b
_.r=c
_.y=d
_.a=e},
dF:function dF(a,b){this.a=a
this.c=b},
j2:function j2(a,b){this.d=a
this.a=b},
nK:function nK(){},
jw:function jw(a,b,c,d){var _=this
_.d=a
_.f=b
_.w=c
_.a=d},
j6:function j6(a,b,c){this.r=a
this.w=b
this.a=c},
j8:function j8(a,b,c){this.d=a
this.e=b
this.a=c},
ja:function ja(a,b,c,d,e){var _=this
_.e=a
_.f=b
_.x=c
_.y=d
_.a=e},
rQ:function rQ(a,b){this.a=a
this.b=b},
rS:function rS(a,b){this.a=a
this.b=b},
dG:function dG(a,b,c,d,e){var _=this
_.d=a
_.e=b
_.f=c
_.z=d
_.a=e},
i2:function i2(){this.d=$
this.c=this.a=null},
u3:function u3(a){this.a=a},
eu:function eu(a,b,c,d,e,f,g){var _=this
_.d=a
_.e=b
_.f=c
_.r=d
_.w=e
_.x=f
_.a=g},
lS:function lS(){this.c=this.a=null},
u_:function u_(){},
DL(a){var s,r,q=$.An
if(!q){$.An=!0
s=A.a7(A.p(v.G.document).documentElement)
if(s==null)s=A.p(s)
for(q=new A.aC(a,A.n(a).h("aC<1,2>")).gC(0);q.p();){r=q.d
s.setAttribute(r.a,r.b)}}return B.ad},
DK(a){var s,r,q,p,o,n,m,l,k,j,i
if(!$.Ao)s=a.length===0
else s=!0
if(s)return B.ad
$.Ao=!0
s=v.G
r=A.a7(A.p(s.document).head)
r.toString
for(q=a.length,p=0;p<a.length;a.length===q||(0,A.I)(a),++p){o=a[p]
n=o.a
if(n==="link"){m=A.p(A.p(s.document).createElement("link"))
n=o.b
if(n!=null)for(n=new A.cH(n,n.r,n.e,A.n(n).h("cH<1,2>"));n.p();){l=n.d
m.setAttribute(l.a,l.b)}A.p(r.appendChild(m))}else if(n==="meta"){k=A.p(A.p(s.document).createElement("meta"))
n=o.b
if(n!=null)for(n=new A.cH(n,n.r,n.e,A.n(n).h("cH<1,2>"));n.p();){l=n.d
k.setAttribute(l.a,l.b)}A.p(r.appendChild(k))}else if(n==="style"&&o.c!=null){j=A.p(A.p(s.document).createElement("style"))
n=o.c
n.toString
j.textContent=n
A.p(r.appendChild(j))}else if(n==="title"&&o.c!=null){i=A.p(A.p(s.document).createElement("title"))
n=o.c
n.toString
i.textContent=n
A.p(r.appendChild(i))}}A.DJ()
return B.ad},
DJ(){A.nk(A.p(A.p(A.p(v.G.document).fonts).ready),t.m).ah(new A.oK(),t.a)},
cc:function cc(a,b,c){this.a=a
this.b=b
this.c=c},
oK:function oK(){},
B8(a,b,c){return new A.fe(a,c,null,b,null,"span",null)},
lr(a){return new A.fe(a,B.cP,B.aN,B.bv,B.aS,"h4",null)},
fe:function fe(a,b,c,d,e,f,g){var _=this
_.d=a
_.e=b
_.f=c
_.r=d
_.y=e
_.db=f
_.a=g},
ct(a,b){return new A.j4(new A.a6("e0ad",B.aQ,null),b,a,null)},
j4:function j4(a,b,c,d){var _=this
_.d=a
_.e=b
_.f=c
_.a=d},
ev:function ev(a,b,c,d,e,f){var _=this
_.e=a
_.f=b
_.r=c
_.x=d
_.ay=e
_.a=f},
i1:function i1(){var _=this
_.d=!1
_.c=_.a=_.f=_.e=null},
u2:function u2(a,b){this.a=a
this.b=b},
u1:function u1(a){this.a=a},
u0:function u0(a){this.a=a},
Ec(a){var s
switch(a.a){case 0:s=12
break
case 1:s=16
break
case 2:s=20
break
case 3:s=24
break
case 4:s=32
break
case 5:s=48
break
default:s=null}return s},
zH(a){return new A.a6("e038",t.W.a(a),null)},
zI(a){return new A.a6("e059",t.W.a(a),null)},
zJ(a){return new A.a6("e2d0",t.W.a(a),null)},
zK(a){return new A.a6("e36a",t.W.a(a),null)},
zL(a){return new A.a6("e081",t.W.a(a),null)},
zM(a){return new A.a6("e30b",t.W.a(a),null)},
zN(a){return new A.a6("e0a9",t.W.a(a),null)},
zO(a){return new A.a6("e1bf",t.W.a(a),null)},
zP(a){return new A.a6("e359",t.W.a(a),null)},
zQ(a){return new A.a6("e0e8",t.W.a(a),null)},
zR(a){return new A.a6("e0e9",t.W.a(a),null)},
zS(a){return new A.a6("e1c1",t.W.a(a),null)},
zT(a){return new A.a6("e445",t.W.a(a),null)},
zU(a){return new A.a6("e13c",t.W.a(a),null)},
zV(a){return new A.a6("e37f",t.W.a(a),null)},
zW(a){return new A.a6("e286",t.W.a(a),null)},
zX(a){return new A.a6("e45f",t.W.a(a),null)},
zY(a){return new A.a6("e341",t.W.a(a),null)},
zZ(a){return new A.a6("e154",t.W.a(a),null)},
A_(a){return new A.a6("e245",t.W.a(a),null)},
A0(a){return new A.a6("e25f",t.W.a(a),null)},
A1(a){return new A.a6("e2ef",t.W.a(a),null)},
A2(a){return new A.a6("e29a",t.W.a(a),null)},
A3(a){return new A.a6("e186",t.W.a(a),null)},
A4(a){return new A.a6("e193",t.W.a(a),null)},
A5(a){return new A.a6("e1b4",t.W.a(a),null)},
cd:function cd(a,b){this.a=a
this.b=b},
a6:function a6(a,b,c){this.d=a
this.e=b
this.a=c},
Hj(a){var s,r,q=A.a([],t.s)
for(s=0;s<1;++s){r=a[s].mh()
if(r.length===0)continue
B.b.m(q,r)}return B.b.aA(q,";")},
BZ(a){return A.mO(2,a,B.l,!1)},
fN:function fN(){},
ih:function ih(a,b,c){this.a=a
this.b=b
this.c=c},
dE:function dE(a,b,c){this.a=a
this.b=b
this.c=c},
nu:function nu(a,b){this.a=a
this.b=b},
nt:function nt(a,b,c){this.a=a
this.b=b
this.e=c},
dJ:function dJ(a,b){this.a=a
this.b=b},
jo:function jo(a,b){this.a=a
this.b=b},
fV:function fV(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=g
_.w=h
_.x=i
_.y=j
_.z=k
_.Q=l
_.as=m
_.at=n
_.ax=o},
jr:function jr(a,b){this.a=a
this.b=b},
od:function od(a,b,c,d,e,f,g,h){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=g
_.w=h},
oe:function oe(a,b,c,d,e,f,g,h,i,j){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=g
_.w=h
_.x=i
_.y=j},
or:function or(a,b,c,d,e,f,g,h){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=g
_.w=h},
oJ:function oJ(a,b,c,d,e){var _=this
_.c=a
_.d=b
_.e=c
_.r=d
_.w=e},
hP:function hP(a,b){this.a=a
this.b=b},
hQ:function hQ(a,b){this.a=a
this.b=b},
rR:function rR(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=g
_.w=h
_.x=i
_.y=j
_.z=k
_.Q=l
_.as=m
_.at=n
_.ax=o
_.ay=p
_.ch=q
_.CW=r},
jJ:function jJ(a,b){this.a=a
this.b=b},
oV:function oV(a,b){this.a=a
this.b=b},
oU:function oU(a,b,c,d,e,f,g){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=g},
jU:function jU(a,b){this.a=a
this.b=b},
pg:function pg(a,b){this.a=a
this.b=b},
ph:function ph(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=g
_.x=h
_.y=i
_.z=j
_.Q=k
_.as=l
_.ay=m
_.ch=n
_.CW=o},
cg:function cg(a,b,c){this.a=a
this.b=b
this.c=c},
qz:function qz(a,b,c,d,e,f,g,h,i,j,k,l){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=g
_.w=h
_.x=i
_.y=j
_.z=k
_.Q=l},
nN:function nN(a,b,c,d,e,f,g,h){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=g
_.w=h},
rz:function rz(a,b){this.a=a
this.b=b},
rB:function rB(a,b){this.a=a
this.b=b},
rA:function rA(a,b){this.a=a
this.b=b},
ry:function ry(a,b,c,d,e,f,g,h,i,j,k){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=g
_.Q=h
_.as=i
_.at=j
_.ax=k},
rT:function rT(a,b,c,d,e,f,g,h,i){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.f=e
_.r=f
_.w=g
_.x=h
_.y=i},
bv:function bv(a,b){this.a=a
this.b=b},
rY:function rY(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=g
_.w=h
_.x=i
_.y=j
_.z=k
_.Q=l
_.as=m
_.at=n
_.ax=o},
cl:function cl(a,b){this.a=a
this.b=b},
lw:function lw(a,b){this.a=a
this.b=b},
t4:function t4(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,a0,a1,a2,a3,a4){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.w=g
_.x=h
_.y=i
_.z=j
_.Q=k
_.as=l
_.at=m
_.ax=n
_.ay=o
_.ch=p
_.CW=q
_.cx=r
_.cy=s
_.db=a0
_.dx=a1
_.dy=a2
_.fr=a3
_.fx=a4},
ff:function ff(a,b){this.a=a
this.b=b},
ta:function ta(a,b){this.a=a
this.b=b},
e6:function e6(a,b,c,d,e,f,g,h,i,j){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=g
_.w=h
_.x=i
_.y=j},
t9:function t9(a,b,c,d,e){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e},
tb:function tb(a,b,c,d,e,f,g,h,i,j){var _=this
_.a=a
_.b=b
_.c=c
_.e=d
_.f=e
_.r=f
_.w=g
_.x=h
_.y=i
_.z=j},
jn:function jn(){},
o5:function o5(a,b){this.a=a
this.b=b},
jq:function jq(){},
js:function js(){},
of:function of(a){this.a=a},
jD:function jD(){},
jI:function jI(){},
jT:function jT(){},
pi:function pi(a){this.a=a},
pj:function pj(a){this.a=a},
pk:function pk(a){this.a=a},
kr:function kr(){},
qA:function qA(){},
kU:function kU(){},
lk:function lk(){},
lv:function lv(){},
t5:function t5(a){this.a=a},
t6:function t6(a){this.a=a},
ly:function ly(){},
oi:function oi(a,b){this.a=a
this.b=b},
oh:function oh(a,b){this.a=a
this.b=b},
b5(a){var s=a.H(t.cC)
if(s==null)throw A.d(A.cU("No ArcaneThemeProvider found in context. Wrap your app with ArcaneThemeProvider or ArcaneApp."))
return s.f},
o1:function o1(a,b){this.a=a
this.b=b},
fO:function fO(a,b,c,d){var _=this
_.f=a
_.r=b
_.b=c
_.a=d},
li:function li(){},
lg:function lg(){},
k3:function k3(){},
jc:function jc(){},
lx:function lx(a,b,c,d,e,f,g,h,i,j,k,l){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=g
_.w=h
_.x=i
_.y=j
_.z=k
_.Q=l},
jV:function jV(a){this.a=a},
t7:function t7(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,a0,a1,a2,a3,a4,a5,a6,a7,a8,a9,b0,b1,b2,b3,b4,b5,b6,b7,b8,b9,c0,c1,c2,c3,c4,c5,c6){var _=this
_.b=a
_.c=b
_.d=c
_.e=d
_.f=e
_.r=f
_.w=g
_.x=h
_.y=i
_.z=j
_.Q=k
_.as=l
_.at=m
_.ax=n
_.ay=o
_.ch=p
_.CW=q
_.cx=r
_.cy=s
_.db=a0
_.dx=a1
_.dy=a2
_.fr=a3
_.fx=a4
_.fy=a5
_.go=a6
_.id=a7
_.k1=a8
_.k2=a9
_.k3=b0
_.k4=b1
_.ok=b2
_.p1=b3
_.p2=b4
_.p3=b5
_.p4=b6
_.R8=b7
_.RG=b8
_.rx=b9
_.ry=c0
_.to=c1
_.x1=c2
_.x2=c3
_.xr=c4
_.y1=c5
_.y2=c6},
qK:function qK(){},
qn:function qn(a,b){this.a=a
this.b=b},
oH:function oH(a,b){this.a=a
this.b=b},
qo:function qo(a,b){this.a=a
this.b=b},
pl:function pl(a,b){this.a=a
this.b=b},
j7:function j7(a){this.a=a},
ls:function ls(a,b){this.a=a
this.b=b},
he:function he(a,b){this.a=a
this.b=b},
qb:function qb(a,b){this.a=a
this.b=b},
kV:function kV(a,b){this.c=a
this.a=b},
f9:function f9(a,b){this.c=a
this.a=b},
kW:function kW(a,b){this.c=a
this.a=b},
kX:function kX(a,b){this.c=a
this.a=b},
kY:function kY(a,b){this.c=a
this.a=b},
kZ:function kZ(a,b){this.c=a
this.a=b},
rI:function rI(a){this.a=a},
rJ:function rJ(){},
rK:function rK(a){this.a=a},
l3:function l3(a,b){this.c=a
this.a=b},
rL:function rL(a){this.a=a},
rM:function rM(){},
rN:function rN(a){this.a=a},
l_:function l_(a,b){this.c=a
this.a=b},
l0:function l0(a,b){this.c=a
this.a=b},
l1:function l1(a,b){this.c=a
this.a=b},
l2:function l2(a,b){this.c=a
this.a=b},
l4:function l4(a,b){this.c=a
this.a=b},
l5:function l5(a,b){this.c=a
this.a=b},
l7:function l7(a,b){this.c=a
this.a=b},
l8:function l8(a,b){this.c=a
this.a=b},
l9:function l9(a,b){this.c=a
this.a=b},
la:function la(a,b){this.c=a
this.a=b},
rP:function rP(a){this.a=a},
l6:function l6(){},
rO:function rO(a,b){this.a=a
this.b=b},
U:function U(){},
o8:function o8(a){this.a=a},
o9:function o9(a){this.a=a},
oa:function oa(a,b){this.a=a
this.b=b},
ob:function ob(a){this.a=a},
oc:function oc(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.d=d},
kN:function kN(a,b){this.a=a
this.b=b},
jk:function jk(){},
fS:function fS(){},
nZ:function nZ(){},
o_:function o_(){},
o0:function o0(){},
Cl(a,b){var s
if(t.m.b(a)&&"AbortError"===A.r(a.name))return new A.kN("Request aborted by `abortTrigger`",b.b)
if(!(a instanceof A.c8)){s=J.aF(a)
if(B.a.M(s,"TypeError: "))s=B.a.S(s,11)
a=new A.c8(s,b.b)}return a},
Cc(a,b,c){A.Ar(A.Cl(a,c),b)},
G3(a,b){return new A.il(new A.xc(a,b),t.e6)},
fz(a,b,c){return A.GK(a,b,c)},
GK(a3,a4,a5){var s=0,r=A.Q(t.H),q,p=2,o=[],n,m,l,k,j,i,h,g,f,e,d,c,b,a,a0,a1,a2
var $async$fz=A.R(function(a6,a7){if(a6===1){o.push(a7)
s=p}for(;;)switch(s){case 0:a={}
a0=A.a7(a4.body)
a1=a0==null?null:A.p(a0.getReader())
s=a1==null?3:4
break
case 3:s=5
return A.G(a5.a_(),$async$fz)
case 5:s=1
break
case 4:a.a=null
a.b=a.c=!1
a5.smV(new A.xm(a))
a5.smT(new A.xn(a,a1,a3))
a0=t.hD,k=a5.$ti,j=k.c,i=t.m,k=k.h("d1<1>"),h=t.gL,g=t.cU,f=t.ou
case 6:n=null
p=9
s=12
return A.G(A.nk(A.p(a1.read()),i),$async$fz)
case 12:n=a7
p=2
s=11
break
case 9:p=8
a2=o.pop()
m=A.a1(a2)
l=A.b3(a2)
s=!a.c?13:14
break
case 13:a.b=!0
a0=A.Cl(m,a3)
j=t.fw.a(l)
i=a5.b
if(i>=4)A.ak(a5.cN())
if((i&1)!==0){d=a5.a
g=k.a((i&8)!==0?h.a(d).gbE():d)
g.ja(a0,j==null?B.M:j)}s=15
return A.G(a5.a_(),$async$fz)
case 15:case 14:s=7
break
s=11
break
case 8:s=2
break
case 11:if(A.dz(n.done)){a5.m0()
s=7
break}else{c=n.value
c.toString
c=j.a(a0.a(c))
b=a5.b
if(b>=4)A.ak(a5.cN())
if((b&1)!==0){d=a5.a
k.a((b&8)!==0?h.a(d).gbE():d).dZ(c)}}c=a5.b
if((c&1)!==0){d=a5.a
b=(k.a((c&8)!==0?h.a(d).gbE():d).e&4)!==0
c=b}else c=(c&2)===0
s=c?16:17
break
case 16:c=a.a
s=18
return A.G((c==null?a.a=new A.c3(new A.a_($.a0,g),f):c).a,$async$fz)
case 18:case 17:if((a5.b&1)===0){s=7
break}s=6
break
case 7:case 1:return A.O(q,r)
case 2:return A.N(o.at(-1),r)}})
return A.P($async$fz,r)},
jl:function jl(a){this.c=a},
o2:function o2(a){this.a=a},
xc:function xc(a,b){this.a=a
this.b=b},
xm:function xm(a){this.a=a},
xn:function xn(a,b,c){this.a=a
this.b=b
this.c=c},
ey:function ey(a){this.a=a},
o7:function o7(a){this.a=a},
Dx(a,b){return new A.c8(a,b)},
c8:function c8(a,b){this.a=a
this.b=b},
EI(a,b){var s=new Uint8Array(0),r=$.CR()
if(!r.b.test(a))A.ak(A.dH(a,"method","Not a valid method"))
r=t.N
return new A.kM(B.l,s,a,b,A.ke(new A.nZ(),new A.o_(),r,r))},
kM:function kM(a,b,c,d,e){var _=this
_.x=a
_.y=b
_.a=c
_.b=d
_.r=e
_.w=!1},
re(a){var s=0,r=A.Q(t.cD),q,p,o,n,m,l,k,j
var $async$re=A.R(function(b,c){if(b===1)return A.N(c,r)
for(;;)switch(s){case 0:s=3
return A.G(a.w.ia(),$async$re)
case 3:p=c
o=a.b
n=a.a
m=a.e
l=a.c
k=A.CO(p)
j=p.length
k=new A.kO(k,n,o,l,j,m,!1,!0)
k.fu(o,j,m,!1,!0,l,n)
q=k
s=1
break
case 1:return A.O(q,r)}})
return A.P($async$re,r)},
bs(a){var s=a.j(0,"content-type")
if(s!=null)return A.AC(s)
return A.qt("application","octet-stream",null)},
kO:function kO(a,b,c,d,e,f,g,h){var _=this
_.w=a
_.a=b
_.b=c
_.c=d
_.d=e
_.e=f
_.f=g
_.r=h},
hU:function hU(){},
ll:function ll(a,b,c,d,e,f,g,h){var _=this
_.w=a
_.a=b
_.b=c
_.c=d
_.d=e
_.e=f
_.f=g
_.r=h},
Dw(a){return A.r(a).toLowerCase()},
fX:function fX(a,b,c){this.a=a
this.c=b
this.$ti=c},
AC(a){return A.In("media type",a,new A.qu(a),t.br)},
qt(a,b,c){var s=t.N
if(c==null)s=A.t(s,s)
else{s=new A.fX(A.H6(),A.t(s,t.gc),t.kj)
s.B(0,c)}return new A.eW(a.toLowerCase(),b.toLowerCase(),new A.d_(s,t.ph))},
eW:function eW(a,b,c){this.a=a
this.b=b
this.c=c},
qu:function qu(a){this.a=a},
qw:function qw(a){this.a=a},
qv:function qv(){},
Hl(a){var s
a.hG($.Df(),"quoted string")
s=a.geY().j(0,0)
return A.CM(B.a.q(s,1,s.length-1),$.De(),t.jt.a(t.po.a(new A.xY())),null)},
xY:function xY(){},
fZ:function fZ(a,b,c){var _=this
_.c=$
_.d=null
_.c$=a
_.a$=b
_.b$=c},
og:function og(){},
lX:function lX(){},
DN(a,b){var s=new A.h7()
s.a=b
s.cQ(a)
return s},
EK(a,b){var s=new A.kP(a,A.a([],t.O)),r=b==null?A.qB(A.p(a.childNodes)):b,q=t.m
r=A.x(r,q)
s.k3$=r
r=A.yH(r,q)
s.e=r==null?null:A.a7(r.previousSibling)
return s},
DR(a,b,c){var s=new A.jO(b,c)
s.j1(a,b,c)
return s},
nX(a,b,c){if(c==null){if(!A.dz(a.hasAttribute(b)))return
a.removeAttribute(b)}else{if(A.aA(a.getAttribute(b))===c)return
a.setAttribute(b,c)}},
bW:function bW(){},
jH:function jH(a){var _=this
_.d=$
_.e=null
_.k3$=a
_.c=_.b=_.a=null},
oL:function oL(a){this.a=a},
oM:function oM(){},
oN:function oN(a,b,c){this.a=a
this.b=b
this.c=c},
h7:function h7(){var _=this
_.d=$
_.c=_.b=_.a=null},
oO:function oO(){},
bV:function bV(a,b){var _=this
_.d=a
_.e=!1
_.r=_.f=null
_.k3$=b
_.c=_.b=_.a=null},
kP:function kP(a,b){var _=this
_.d=a
_.e=$
_.k3$=b
_.c=_.b=_.a=null},
cK:function cK(){},
cD:function cD(){},
jO:function jO(a,b){this.a=a
this.b=b
this.c=null},
oW:function oW(a){this.a=a},
m1:function m1(){},
m2:function m2(){},
m3:function m3(){},
m4:function m4(){},
mA:function mA(){},
mB:function mB(){},
fU:function fU(a,b){this.c=a
this.a=b},
ew(a){var s=$.Aa.j(0,a)
if(s==null){s=new A.jh(a,A.a([],t.ox))
$.Aa.i(0,a,s)}return s},
hh:function hh(a,b,c){this.c=a
this.e=b
this.a=c},
ji:function ji(a,b){this.a=a
this.b=b},
fQ:function fQ(a,b,c,d){var _=this
_.b=a
_.c=b
_.d=c
_.a=d},
lV:function lV(a,b,c,d,e,f,g){var _=this
_.d$=a
_.e$=b
_.f$=c
_.cy=null
_.db=d
_.c=_.b=_.a=null
_.d=e
_.e=null
_.f=f
_.w=_.r=null
_.x=g
_.Q=_.z=_.y=null
_.as=!1
_.at=!0
_.ax=!1
_.CW=null
_.cx=!1},
c7:function c7(a,b,c){var _=this
_.w=a
_.x=b
_.y=null
_.z=c
_.d=$
_.c=_.b=_.a=null},
jh:function jh(a,b){var _=this
_.a=a
_.e=_.d=_.c=_.b=$
_.f=b
_.r=!0},
nV:function nV(a){this.a=a},
nW:function nW(){},
zf(a,b,c,d){return new A.mX(c,d,b,a,null)},
CD(a,b,c){return new A.n4(b,c,a,null)},
I4(a,b,c){return new A.ne(b,c,a,null)},
I7(a,b,c){return new A.ng(b,c,a,null)},
zq(a,b,c){return new A.nh(b,c,a,null)},
fE(a,b,c,d,e,f,g){return new A.mZ(g,e,c,f,b,d,a,null)},
HY(a,b,c,d,e,f,g,h){return new A.iU(g,e,d,b,f,a,c,null,h.h("iU<0>"))},
C1(a){var s=null
switch(a){case!0:s="true"
break
case!1:s="false"
break
case null:case void 0:break}return s},
CJ(a,b){return new A.ni(b,a,null)},
H(a,b,c,d,e){return new A.ep(d,c,e,b,a,null)},
mX:function mX(a,b,c,d,e){var _=this
_.d=a
_.e=b
_.f=c
_.w=d
_.a=e},
n4:function n4(a,b,c,d){var _=this
_.d=a
_.e=b
_.w=c
_.a=d},
n5:function n5(a,b,c,d){var _=this
_.d=a
_.e=b
_.w=c
_.a=d},
n6:function n6(a,b,c,d){var _=this
_.d=a
_.e=b
_.w=c
_.a=d},
n7:function n7(a,b,c,d){var _=this
_.d=a
_.e=b
_.w=c
_.a=d},
n8:function n8(a,b,c,d){var _=this
_.d=a
_.e=b
_.w=c
_.a=d},
n9:function n9(a,b,c,d){var _=this
_.d=a
_.e=b
_.w=c
_.a=d},
ne:function ne(a,b,c,d){var _=this
_.d=a
_.e=b
_.w=c
_.a=d},
ng:function ng(a,b,c,d){var _=this
_.d=a
_.e=b
_.w=c
_.a=d},
nl:function nl(a,b,c){this.d=a
this.w=b
this.a=c},
c:function c(a,b,c,d,e,f,g){var _=this
_.c=a
_.d=b
_.e=c
_.f=d
_.r=e
_.w=f
_.a=g},
nh:function nh(a,b,c,d){var _=this
_.d=a
_.e=b
_.w=c
_.a=d},
nj:function nj(a,b,c,d){var _=this
_.d=a
_.e=b
_.w=c
_.a=d},
mZ:function mZ(a,b,c,d,e,f,g,h){var _=this
_.e=a
_.r=b
_.w=c
_.x=d
_.y=e
_.z=f
_.Q=g
_.a=h},
iU:function iU(a,b,c,d,e,f,g,h,i){var _=this
_.c=a
_.d=b
_.z=c
_.Q=d
_.as=e
_.at=f
_.ax=g
_.a=h
_.$ti=i},
cF:function cF(a,b,c){this.c=a
this.a=b
this.b=c},
nb:function nb(a,b,c,d,e){var _=this
_.e=a
_.f=b
_.r=c
_.x=d
_.a=e},
nc:function nc(a){this.a=a},
iX:function iX(a,b,c){this.c=a
this.f=b
this.a=c},
no:function no(a,b,c,d,e){var _=this
_.c=a
_.w=b
_.x=c
_.z=d
_.a=e},
ni:function ni(a,b,c){this.c=a
this.Q=b
this.a=c},
mY:function mY(a){this.a=a},
n0:function n0(a,b,c,d){var _=this
_.d=a
_.e=b
_.w=c
_.a=d},
n2:function n2(a,b,c,d){var _=this
_.d=a
_.e=b
_.w=c
_.a=d},
na:function na(a,b,c){this.e=a
this.w=b
this.a=c},
nm:function nm(a,b,c,d){var _=this
_.d=a
_.e=b
_.w=c
_.a=d},
ep:function ep(a,b,c,d,e,f){var _=this
_.c=a
_.d=b
_.e=c
_.f=d
_.w=e
_.a=f},
nn:function nn(a,b,c,d){var _=this
_.d=a
_.e=b
_.w=c
_.a=d},
kG:function kG(a,b){this.c=a
this.a=b},
it:function it(a,b){this.b=a
this.a=b},
mx:function mx(a,b,c,d,e,f){var _=this
_.d$=a
_.e$=b
_.f$=c
_.c=_.b=_.a=null
_.d=d
_.e=null
_.f=e
_.w=_.r=null
_.x=f
_.Q=_.z=_.y=null
_.as=!1
_.at=!0
_.ax=!1
_.CW=null
_.cx=!1},
m5:function m5(a){var _=this
_.d=a
_.c=_.b=_.a=null},
ud:function ud(){},
i7:function i7(a){this.a=a},
mT:function mT(){},
lQ:function lQ(){},
AE(a){if(a==1/0||a==-1/0)return B.c.k(a).toLowerCase()
return B.c.nb(a)===a?B.c.k(B.c.ac(a)):B.c.k(a)},
fv:function fv(){},
m7:function m7(a,b){this.a=a
this.b=b},
my:function my(a,b){this.a=a
this.b=b},
B(a){var s=null
return new A.l(s,s,s,s,a)},
Gf(a,b){var s=t.N
return a.bs(0,new A.xi(b),s,s)},
ln:function ln(){},
lo:function lo(){},
l:function l(a,b,c,d,e){var _=this
_.as=a
_.mp=b
_.mq=c
_.mr=d
_.ms=e},
xi:function xi(a){this.a=a},
mK:function mK(){},
j1:function j1(){},
lR:function lR(){},
hN:function hN(a,b){this.a=a
this.b=b},
kT:function kT(){},
rx:function rx(a,b){this.a=a
this.b=b},
cm:function cm(a,b){this.a=a
this.$ti=b},
t3:function t3(a){this.a=a},
DM(a,b){if(b==null)return a
return A.w(a)+" "+b},
yz(a,b,c,d){return b},
Fz(a){var s=A.eI(t.Q),r=($.aQ+1)%16777215
$.aQ=r
return new A.iw(null,!1,!1,s,r,a,B.u)},
oj(a,b){if(A.bH(a)!==A.bH(b)||!J.a8(a.a,b.a))return!1
if(a instanceof A.X&&a.b!==t.J.a(b).b)return!1
return!0},
DO(a,b){var s,r=t.Q
r.a(a)
r.a(b)
r=a.e
r.toString
s=b.e
s.toString
if(r<s)return-1
else if(s<r)return 1
else{r=b.at
if(r&&!a.at)return-1
else if(a.at&&!r)return 1}return 0},
Fn(a){a.bJ()
a.b2(A.y_())},
jm:function jm(a,b){var _=this
_.a=a
_.c=_.b=!1
_.d=b
_.e=null},
o3:function o3(a,b){this.a=a
this.b=b},
fT:function fT(){},
X:function X(a,b,c,d,e,f,g,h){var _=this
_.b=a
_.c=b
_.d=c
_.e=d
_.f=e
_.r=f
_.w=g
_.a=h},
jG:function jG(a,b,c,d,e,f,g){var _=this
_.ry=null
_.d$=a
_.e$=b
_.f$=c
_.cy=null
_.db=d
_.c=_.b=_.a=null
_.d=e
_.e=null
_.f=f
_.w=_.r=null
_.x=g
_.Q=_.z=_.y=null
_.as=!1
_.at=!0
_.ax=!1
_.CW=null
_.cx=!1},
k:function k(a,b){this.b=a
this.a=b},
lt:function lt(a,b,c,d,e,f){var _=this
_.d$=a
_.e$=b
_.f$=c
_.c=_.b=_.a=null
_.d=d
_.e=null
_.f=e
_.w=_.r=null
_.x=f
_.Q=_.z=_.y=null
_.as=!1
_.at=!0
_.ax=!1
_.CW=null
_.cx=!1},
bK:function bK(a,b){this.b=a
this.a=b},
me:function me(a,b,c,d,e,f,g){var _=this
_.d$=a
_.e$=b
_.f$=c
_.cy=null
_.db=d
_.c=_.b=_.a=null
_.d=e
_.e=null
_.f=f
_.w=_.r=null
_.x=g
_.Q=_.z=_.y=null
_.as=!1
_.at=!0
_.ax=!1
_.CW=null
_.cx=!1},
jx:function jx(){},
iv:function iv(a,b,c){this.b=a
this.c=b
this.a=c},
iw:function iw(a,b,c,d,e,f,g){var _=this
_.d$=a
_.e$=b
_.f$=c
_.cy=null
_.db=d
_.c=_.b=_.a=null
_.d=e
_.e=null
_.f=f
_.w=_.r=null
_.x=g
_.Q=_.z=_.y=null
_.as=!1
_.at=!0
_.ax=!1
_.CW=null
_.cx=!1},
e:function e(){},
fm:function fm(a,b){this.a=a
this.b=b},
C:function C(){},
oQ:function oQ(a){this.a=a},
oR:function oR(){},
oS:function oS(a){this.a=a},
oT:function oT(a,b){this.a=a
this.b=b},
oP:function oP(){},
de:function de(a,b){this.a=null
this.b=a
this.c=b},
mj:function mj(a){this.a=a},
vi:function vi(a){this.a=a},
aZ:function aZ(){},
hj:function hj(a,b,c,d){var _=this
_.ry=a
_.c=_.b=_.a=_.cy=null
_.d=b
_.e=null
_.f=c
_.w=_.r=null
_.x=d
_.Q=_.z=_.y=null
_.as=!1
_.at=!0
_.ax=!1
_.CW=null
_.cx=!1},
eR:function eR(){},
kf:function kf(){},
e8:function e8(a,b){this.a=a
this.$ti=b},
hu:function hu(){},
hx:function hx(){},
eX:function eX(){},
eS:function eS(){},
bp:function bp(){},
af:function af(){},
M:function M(){},
dU:function dU(){},
hT:function hT(a,b,c,d){var _=this
_.ry=a
_.to=null
_.x1=!1
_.c=_.b=_.a=_.cy=null
_.d=b
_.e=null
_.f=c
_.w=_.r=null
_.x=d
_.Q=_.z=_.y=null
_.as=!1
_.at=!0
_.ax=!1
_.CW=null
_.cx=!1},
rW:function rW(a){this.a=a},
rX:function rX(a){this.a=a},
o:function o(){},
lh:function lh(a,b,c){var _=this
_.c=_.b=_.a=_.cy=_.ry=null
_.d=a
_.e=null
_.f=b
_.w=_.r=null
_.x=c
_.Q=_.z=_.y=null
_.as=!1
_.at=!0
_.ax=!1
_.CW=null
_.cx=!1},
FA(a,b){return new A.ix(a,b)},
rg:function rg(a){this.a=a},
rh:function rh(a,b){this.a=a
this.b=b},
ri:function ri(a,b,c){this.a=a
this.b=b
this.c=c},
ix:function ix(a,b){this.a=a
this.b=b},
mD:function mD(a){this.a=a},
f7:function f7(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.d=d},
EN(a,b,c,d,e){var s,r,q,p,o,n
if(e instanceof A.dq)return new A.cQ(e,d,a,null)
else if(e instanceof A.cP){s=e.x
s===$&&A.S()
r=s.mN(0,d)
if(r==null)return null
q=A.Hm(e.w,r)
for(s=new A.aC(q,A.n(q).h("aC<1,2>")).gC(0);s.p();){p=s.d
o=p.a
n=p.b
c.i(0,o,A.d6(n,0,n.length,B.l,!1))}return new A.cQ(e,A.Cs(b,A.I9(e.b,q)),a,null)}throw A.d(A.AB("Unexpected route type: "+e.k(0),d))},
cQ:function cQ(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.d=d},
EM(a,b,c){return new A.aq(a,A.rn(a),c,b)},
rn(a){var s,r,q,p,o,n=new A.aI("")
for(s=a.length,r=!1,q=0;q<s;++q){p=a[q].a
if(p instanceof A.cP){if(r)n.a+="/"
o=p.b
n.a+=o
r=r||o!=="/"}}s=n.a
return s.charCodeAt(0)==0?s:s},
AB(a,b){return new A.eV(a+": "+b,b)},
C3(a,b,c,d,e,f){var s,r,q,p,o,n,m,l,k=A.Bk(),j=f.length,i=t.N,h=0
for(;;){if(!(h<f.length)){s=null
break}A:{r=f[h]
q=A.t(i,i)
k.b=q
p=A.EN(a,c,q,e,r)
if(p==null)break A
q=p.a
if(q instanceof A.cP&&p.b.toLowerCase()===b.toLowerCase())s=A.a([p],t.E)
else{o=r.a
if(o.length===0)break A
else{if(q instanceof A.dq){n=c
m=e}else{n=p.b
q=n==="/"?0:1
m=B.a.S(b,n.length+q)}q=k.b
if(q===k)A.ak(A.En(""))
l=A.C3(a,b,n,q,m,o)
if(l==null)break A
j=A.a([p],t.E)
B.b.B(j,l)}s=j}break}f.length===j||(0,A.I)(f);++h}if(s!=null)d.B(0,k.hc())
return s},
Cw(a,b){var s=a.gab()
s=A.a([new A.cQ(A.as(new A.xX(),a.k(0)),s,null,new A.dw(b))],t.E)
return new A.aq(s,A.rn(s),B.x,a)},
f8:function f8(a){this.a=a},
aq:function aq(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.d=d},
ro:function ro(){},
eV:function eV(a,b){this.a=a
this.b=b},
xX:function xX(){},
jM:function jM(a,b){this.c=a
this.a=b},
hk:function hk(a,b,c){this.d=a
this.b=b
this.a=c},
eN:function eN(a,b,c){this.d=a
this.b=b
this.a=c},
rj:function rj(a,b){this.a=a
this.b=b},
rk:function rk(a){this.a=a},
Ia(a,b){var s,r,q,p,o,n,m,l,k,j
for(s=$.zy().bF(0,a),s=new A.ds(s.a,s.b,s.c),r=t.F,q=0,p="^";s.p();){o=s.d
n=(o==null?r.a(o):o).b
m=n.index
if(m>q)p+=A.ye(B.a.q(a,q,m))
l=n.length
if(1>=l)return A.f(n,1)
k=n[1]
k.toString
if(2>=l)return A.f(n,2)
j=n[2]
p+=j!=null?A.Ge(j,k):"(?<"+k+">[^/]+)"
B.b.m(b,k)
q=m+n[0].length}s=q<a.length?p+A.ye(B.a.S(a,q)):p
if(!B.a.a8(a,"/"))s+="(?=/|$)"
return A.ar(s.charCodeAt(0)==0?s:s,!1)},
I9(a,b){var s,r,q,p,o,n,m,l
for(s=$.zy().bF(0,a),s=new A.ds(s.a,s.b,s.c),r=t.F,q=0,p="";s.p();p=l){o=s.d
n=(o==null?r.a(o):o).b
m=n.index
if(m>q)p+=B.a.q(a,q,m)
if(1>=n.length)return A.f(n,1)
l=n[1]
l.toString
l=p+A.w(b.j(0,l))
q=m+n[0].length}s=q<a.length?p+B.a.S(a,q):p
return s.charCodeAt(0)==0?s:s},
Ge(a,b){var s,r=A.ar("[:=!]",!0),q=t.po.a(new A.xh())
A.yR(0,0,a.length,"startIndex")
s=A.Ii(a,r,q,0)
return"(?<"+b+">"+s+")"},
Cs(a,b){if(a.length===0)return b
return(a==="/"?"":a)+"/"+b},
Hm(a,b){var s,r,q,p=t.N
p=A.t(p,p)
for(s=0;s<a.length;++s){r=a[s]
q=b.mQ(r)
q.toString
p.i(0,r,q)}return p},
Cq(a){var s=A.bN(a).k(0)
if(B.a.a8(s,"?"))s=B.a.q(s,0,s.length-1)
return B.a.i4(B.a.a8(s,"/")&&s!=="/"&&!B.a.v(s,"?")?B.a.q(s,0,s.length-1):s,"/?","?",1)},
xh:function xh(){},
qJ:function qJ(a,b){this.a=a
this.b=b},
jZ:function jZ(){},
pR:function pR(a){this.a=a},
kQ:function kQ(){},
yf(a,b,c,d,e,f){var s,r,q,p,o,n=null,m={}
m.a=f
t.gC.a(a)
s=t.b
s.a(b)
t.fM.a(c)
t.fu.a(d)
t.ja.a(f)
m.a=f
r=b.d
q=r.k(0)
p=new A.yg(m,q,b,c,d,a,e)
if(f==null)m.a=A.a([b],t.g1)
o=c.c.$2(a,new A.aL(q,r.gab(),n,n,n,B.x,r.gdA(),r.gdB(),e,n))
if(t.jv.b(o))return p.$1(o)
return o.ah(p,s)},
C4(a,b,c,d){var s
if(d>=c.a.length)return null
s=new A.xj(a,b,c,d).$1(null)
return s},
Gl(a,b,c,d,e){var s,r,q,p
try{s=d.mu(a)
J.fJ(e,s)
return s}catch(q){p=A.a1(q)
if(p instanceof A.eV){r=p
p=r
return A.Cw(A.bN(p.b),p.a)}else throw q}},
yg:function yg(a,b,c,d,e,f,g){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=g},
yh:function yh(a,b,c,d,e,f,g){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=g},
xj:function xj(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.d=d},
as(a,b){var s=A.a([],t.s),r=new A.cP(b,a,s,B.dr)
r.x=A.Ia(b,s)
return r},
dY:function dY(){},
cP:function cP(a,b,c,d){var _=this
_.b=a
_.e=b
_.w=c
_.x=$
_.a=d},
dq:function dq(a,b){this.b=a
this.a=b},
EP(a){var s=null,r=new A.dn(a,s)
r.j2(s,s,s,5,a)
return r},
e_(a){var s
if(a instanceof A.hT){s=a.ry
s.toString
s=s instanceof A.dZ}else s=!1
if(s){s=a.ry
s.toString
return t.aJ.a(s)}s=a.H(t.hj)
return s==null?null:s.d},
EL(a){var s,r,q=A.F(a),p=q.h("a3<1>")
q=A.x(new A.a3(a,q.h("y(1)").a(new A.rm()),p),p.h("m.E"))
q.$flags=1
s=q
if(s.length!==0){q=A.a([],t.iw)
for(p=s.length,r=0;r<s.length;s.length===p||(0,A.I)(s),++r)q.push(s[r].a)
return A.DY(q,t.H)}else return new A.cm(null,t.e1)},
dn:function dn(a,b){var _=this
_.c=a
_.x=_.w=_.r=$
_.a=b},
rv:function rv(){},
dZ:function dZ(a){var _=this
_.d=null
_.e=a
_.c=_.a=null},
ru:function ru(a){this.a=a},
rt:function rt(a,b){this.a=a
this.b=b},
rs:function rs(){},
rr:function rr(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.d=d},
rq:function rq(a,b,c,d,e){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e},
rp:function rp(a){this.a=a},
rm:function rm(){},
mE:function mE(){},
aL:function aL(a,b,c,d,e,f,g,h,i,j){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=g
_.w=h
_.x=i
_.y=j},
Ca(a){return a},
Cm(a,b){var s,r,q,p,o,n,m,l
for(s=b.length,r=1;r<s;++r){if(b[r]==null||b[r-1]!=null)continue
for(;s>=1;s=q){q=s-1
if(b[q]!=null)break}p=new A.aI("")
o=a+"("
p.a=o
n=A.F(b)
m=n.h("e3<1>")
l=new A.e3(b,0,s,m)
l.j5(b,0,s,n.c)
m=o+new A.E(l,m.h("b(z.E)").a(new A.xq()),m.h("E<z.E,b>")).aA(0,", ")
p.a=m
p.a=m+("): part "+(r-1)+" was null, but part "+r+" was not.")
throw A.d(A.ai(p.k(0),null))}},
ov:function ov(a){this.a=a},
ow:function ow(){},
ox:function ox(){},
xq:function xq(){},
eP:function eP(){},
kz(a,b){var s,r,q,p,o,n,m=b.ik(a)
b.bd(a)
if(m!=null)a=B.a.S(a,m.length)
s=t.s
r=A.a([],s)
q=A.a([],s)
s=a.length
if(s!==0){if(0>=s)return A.f(a,0)
p=b.aX(a.charCodeAt(0))}else p=!1
if(p){if(0>=s)return A.f(a,0)
B.b.m(q,a[0])
o=1}else{B.b.m(q,"")
o=0}for(n=o;n<s;++n)if(b.aX(a.charCodeAt(n))){B.b.m(r,B.a.q(a,o,n))
B.b.m(q,a[n])
o=n+1}if(o<s){B.b.m(r,B.a.S(a,o))
B.b.m(q,"")}return new A.qH(b,m,r,q)},
qH:function qH(a,b,c,d){var _=this
_.a=a
_.b=b
_.d=c
_.e=d},
AK(a){return new A.kA(a)},
kA:function kA(a){this.a=a},
F0(){var s,r,q,p,o,n,m,l,k=null
if(A.yY().gam()!=="file")return $.iZ()
if(!B.a.a8(A.yY().gab(),"/"))return $.iZ()
s=A.BK(k,0,0)
r=A.BH(k,0,0,!1)
q=A.BJ(k,0,0,k)
p=A.BG(k,0,0)
o=A.wT(k,"")
if(r==null)if(s.length===0)n=o!=null
else n=!0
else n=!1
if(n)r=""
n=r==null
m=!n
l=A.BI("a/b",0,3,k,"",m)
if(n&&!B.a.M(l,"/"))l=A.z8(l,m)
else l=A.ek(l)
if(A.iN("",s,n&&B.a.M(l,"//")?"":r,o,l,q,p).fc()==="a\\b")return $.nq()
return $.CU()},
t2:function t2(){},
kD:function kD(a,b,c){this.d=a
this.e=b
this.f=c},
lE:function lE(a,b,c,d){var _=this
_.d=a
_.e=b
_.f=c
_.r=d},
lH:function lH(a,b,c,d){var _=this
_.d=a
_.e=b
_.f=c
_.r=d},
H4(){return A.a([A.as(new A.xt(),"/"),A.as(new A.xu(),"/settings"),A.as(new A.xv(),"/add-server"),A.as(new A.xG(),"/alerts"),A.as(new A.xM(),"/comparison"),A.as(new A.xN(),"/server/:id/overview"),A.as(new A.xO(),"/server/:id/performance"),A.as(new A.xP(),"/server/:id/memory"),A.as(new A.xQ(),"/server/:id/entities"),A.as(new A.xR(),"/server/:id/chunks"),A.as(new A.xS(),"/server/:id/mechanics"),A.as(new A.xw(),"/server/:id/events"),A.as(new A.xx(),"/server/:id/internals"),A.as(new A.xy(),"/server/:id/incidents"),A.as(new A.xz(),"/server/:id/worlds"),A.as(new A.xA(),"/server/:id/integrations"),A.as(new A.xB(),"/server/:id/heatmaps"),A.as(new A.xC(),"/server/:id/optimization"),A.as(new A.xD(),"/server/:id/tweaks"),A.as(new A.xE(),"/server/:id/governors"),A.as(new A.xF(),"/server/:id/world-overrides"),A.as(new A.xH(),"/server/:id/actions"),A.as(new A.xI(),"/server/:id/incident-center"),A.as(new A.xJ(),"/server/:id/environment"),A.as(new A.xK(),"/server/:id/config"),A.as(new A.xL(),"/server/:id/logs")],t.kV)},
H5(a,b){return A.a([new A.dq(new A.xT(b,a),A.H4())],t.kV)},
G5(a){var s=a.H(t.T)
if(s==null||s.d.length===0)return B.kk
return B.cG},
G4(a){var s,r=A.cA(a)
if(r==null)return A.ct("The server fleet has not been initialized yet.","Fleet unavailable")
s=r.e
s===$&&A.S()
return new A.es(s,null)},
aO(a,b,c){var s,r,q,p,o,n,m=null,l=b.f.j(0,"id"),k=A.cA(a),j=m
if(!(l==null))if(!(k==null)){s=k.e
s===$&&A.S()
s=s.e.j(0,l)
j=s}if(j==null)return A.ct("This server is not part of the live fleet. Pair it from the sidebar.","Server not connected")
s=k.e
s===$&&A.S()
l.toString
r=s.i7(l)
q=s.f.j(0,l)
p=q instanceof A.ci?q:m
o=s.mM(l)
q=s.f.j(0,l)
n=q instanceof A.ci?q:m
q=s.f.j(0,l)
s=q instanceof A.ci?q:m
return new A.dX(r,new A.hD(p,o,new A.h6(n,new A.hi(s,new A.dS(j,c,m),m),m),m),m)},
aN(a,b,c){return new A.mt(a,b,c)},
c0:function c0(a,b,c){this.a=a
this.b=b
this.c=c},
xt:function xt(){},
xu:function xu(){},
xv:function xv(){},
xG:function xG(){},
xM:function xM(){},
xN:function xN(){},
xO:function xO(){},
xP:function xP(){},
xQ:function xQ(){},
xR:function xR(){},
xS:function xS(){},
xw:function xw(){},
xx:function xx(){},
xy:function xy(){},
xz:function xz(){},
xA:function xA(){},
xB:function xB(){},
xC:function xC(){},
xD:function xD(){},
xE:function xE(){},
xF:function xF(){},
xH:function xH(){},
xI:function xI(){},
xJ:function xJ(){},
xK:function xK(){},
xL:function xL(){},
xT:function xT(a,b){this.a=a
this.b=b},
kH:function kH(a,b){this.d=a
this.a=b},
dS:function dS(a,b,c){this.d=a
this.e=b
this.a=c},
mr:function mr(){var _=this
_.d=null
_.e=$
_.c=_.a=_.r=_.f=null},
vu:function vu(a){this.a=a},
vt:function vt(a,b){this.a=a
this.b=b},
vv:function vv(a){this.a=a},
vs:function vs(a,b){this.a=a
this.b=b},
mt:function mt(a,b,c){this.a=a
this.b=b
this.c=c},
is:function is(a,b,c,d,e){var _=this
_.d=a
_.e=b
_.f=c
_.r=d
_.a=e},
vF:function vF(a){this.a=a},
f4:function f4(a,b,c,d,e){var _=this
_.d=a
_.e=b
_.f=c
_.r=d
_.a=e},
r7:function r7(a){this.a=a},
r8:function r8(){},
r4:function r4(){},
r5:function r5(){},
r6:function r6(){},
ra:function ra(){},
rb:function rb(){},
rc:function rc(a,b,c){this.a=a
this.b=b
this.c=c},
r9:function r9(a,b){this.a=a
this.b=b},
r3:function r3(a){this.a=a},
rd:function rd(a,b,c){this.a=a
this.b=b
this.c=c},
ma:function ma(a){this.a=a},
uF:function uF(a){this.a=a},
uG:function uG(a){this.a=a},
mv:function mv(a,b,c){this.d=a
this.e=b
this.a=c},
vG:function vG(){},
bO:function bO(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=$},
f3:function f3(a,b){this.d=a
this.a=b},
hI:function hI(a){var _=this
_.d=a
_.r=_.f=_.e=$
_.w=0
_.c=_.a=null},
qS:function qS(a){this.a=a},
qT:function qT(a,b,c){this.a=a
this.b=b
this.c=c},
qU:function qU(a,b){this.a=a
this.b=b},
r1:function r1(a){this.a=a},
r2:function r2(a,b){this.a=a
this.b=b},
qZ:function qZ(a){this.a=a},
r_:function r_(a,b){this.a=a
this.b=b},
r0:function r0(a,b){this.a=a
this.b=b},
qX:function qX(a,b,c){this.a=a
this.b=b
this.c=c},
qW:function qW(a){this.a=a},
qV:function qV(){},
qY:function qY(){},
lp:function lp(a,b){this.c=a
this.a=b},
ay:function ay(a,b,c){this.c=a
this.d=b
this.a=c},
iF:function iF(){var _=this
_.d=$
_.e=null
_.r=_.f=!1
_.c=_.a=null},
wJ:function wJ(a){this.a=a},
wK:function wK(a,b){this.a=a
this.b=b},
BW(a){var s,r,q,p,o,n,m,l,k,j,i=a.length
if(i===0)s=0
else{if(0>=i)return A.f(a,0)
s=J.b4(a[0].b)}i=t.r
r=J.Au(s,i)
for(q=0;q<s;++q)r[q]=q
p=A.F(r)
o=p.h("E<1,v>")
n=A.x(new A.E(r,p.h("v(1)").a(new A.xd()),o),o.h("z.E"))
m=A.a([n],t.bb)
for(p=a.length,l=0;l<a.length;a.length===p||(0,A.I)(a),++l){o=a[l]
k=null
j=o.b
k=j
o=J.aU(k,new A.xe(),i)
o=A.x(o,o.$ti.h("z.E"))
B.b.m(m,o)}return m},
G6(a,b,c){var s,r,q,p,o,n,m={}
m.width=b
m.height=c
s=A.a([{}],t.O)
for(r=a.length,q=0;q<a.length;a.length===r||(0,A.I)(a),++q){p=a[q].a
o=p
n={}
n.label=o
B.b.m(s,n)}m.series=s
return m},
F3(a,b,c){var s,r
try{s=A.p(new v.G.uPlot(a,b,c))
return new A.tg(s)}catch(r){return null}},
F2(a,b,c){var s,r=A.a7(A.p(v.G.document).getElementById(b))
if(r==null)return null
s=A.bb(r.clientWidth)
return A.F3(A.G6(c,s>0?s:600,a),A.BW(c),r)},
xd:function xd(){},
xe:function xe(){},
tg:function tg(a){this.a=a},
I3(){var s=new A.lG(),r=A.DU(new A.y7(s),null,s),q=new A.fZ(null,B.bt,A.a([],t.u))
q.c="body"
q.iK(new A.kH(r,null))},
y7:function y7(a){this.a=a},
Dr(a){var s=A.r(a.j(0,"key")),r=A.r(a.j(0,"label")),q=A.Ax(A.r(a.j(0,"type"))),p=A.x8(a.j(0,"required")),o=a.j(0,"default"),n=t.g.a(a.j(0,"options"))
if(n==null)n=null
else{n=J.aU(n,new A.nx(),t.N)
n=A.x(n,n.$ti.h("z.E"))}if(n==null)n=B.P
return new A.cr(s,r,q,p===!0,o,n)},
Dq(a){var s,r,q=A.r(a.j(0,"id")),p=A.r(a.j(0,"name")),o=A.aA(a.j(0,"description"))
if(o==null)o=""
s=A.x8(a.j(0,"destructive"))
r=t.g.a(a.j(0,"params"))
if(r==null)r=null
else{r=J.aU(r,new A.nv(),t.fS)
r=A.x(r,r.$ti.h("z.E"))}if(r==null)r=B.dw
return new A.da(q,p,o,s===!0,r)},
cr:function cr(a,b,c,d,e,f){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f},
nx:function nx(){},
da:function da(a,b,c,d,e){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e},
nv:function nv(){},
nw:function nw(){},
j_:function j_(a,b){this.a=a
this.b=b},
iT(a){switch(a.a){case 2:return 2
case 1:return 1
case 0:return 0}},
cs:function cs(a,b){this.a=a
this.b=b},
bm:function bm(a,b,c,d,e,f,g,h,i,j){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=g
_.w=h
_.x=i
_.y=j},
fL:function fL(a,b,c,d,e,f,g){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=g},
DE(a){var s=A.r(a.j(0,"name")),r=t.g.a(a.j(0,"nodes"))
if(r==null)r=null
else{r=J.aU(r,new A.om(),t.mv)
r=A.x(r,r.$ti.h("z.E"))}return new A.cy(s,r==null?B.aY:r)},
yx(a){var s=t.g.a(a.j(0,"sections"))
if(s==null)s=null
else{s=J.aU(s,new A.op(),t.ap)
s=A.x(s,s.$ti.h("z.E"))}return new A.h0(s==null?B.aX:s)},
cy:function cy(a,b){this.a=a
this.b=b},
om:function om(){},
on:function on(){},
h0:function h0(a){this.a=a},
op:function op(){},
oq:function oq(){},
jE(a){var s,r=A.r(a.j(0,"id")),q=A.r(a.j(0,"name")),p=A.r(a.j(0,"category")),o=A.dz(a.j(0,"enabled")),n=A.aA(a.j(0,"description"))
if(n==null)n=""
s=t.g.a(a.j(0,"knobs"))
if(s==null)s=null
else{s=J.aU(s,new A.oy(),t.mv)
s=A.x(s,s.$ti.h("z.E"))}return new A.bw(r,q,p,o,n,s==null?B.aY:s)},
bw:function bw(a,b,c,d,e,f){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f},
oy:function oy(){},
oz:function oz(){},
oA:function oA(a,b){this.a=a
this.b=b},
DP(a){var s,r,q,p,o,n=t.N,m=A.ke(null,null,n,t.G)
for(s=a.gaF(),s=s.gC(s),r=t.av,q=t.X;s.p();){p=s.gu()
o=p.b
if(r.b(o))m.i(0,p.a,A.qe(o,n,q))}return new A.eD(m)},
eD:function eD(a){this.a=a},
E0(a){var s,r,q,p,o,n,m,l
A.r(a.j(0,"id"))
s=A.r(a.j(0,"label"))
r=A.aA(a.j(0,"world"))
if(r==null)r=""
q=B.e.bv(A.at(a.j(0,"centerChunkX")))
p=B.e.bv(A.at(a.j(0,"centerChunkZ")))
o=B.e.bv(A.at(a.j(0,"radius")))
n=A.at(a.j(0,"min"))
m=A.at(a.j(0,"max"))
l=t.g.a(a.j(0,"cells"))
if(l==null)l=B.dt
l=J.aU(l,new A.pu(),t.kN)
l=A.x(l,l.$ti.h("z.E"))
return new A.cC(s,r,q,p,o,n,m,l)},
eJ:function eJ(a,b,c){this.a=a
this.b=b
this.c=c},
dO:function dO(a){this.a=a},
cC:function cC(a,b,c,d,e,f,g,h){var _=this
_.b=a
_.c=b
_.d=c
_.e=d
_.f=e
_.r=f
_.w=g
_.x=h},
pu:function pu(){},
Ed(a){return new A.k_(A.r(a.j(0,"serverName")),A.r(a.j(0,"version")),A.dz(a.j(0,"folia")),A.r(a.j(0,"serverId")))},
k_:function k_(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.d=d},
Ee(a){var s=A.at(a.j(0,"score")),r=A.r(a.j(0,"state")),q=t.g,p=q.a(a.j(0,"timeline"))
if(p==null)p=null
else{p=J.aU(p,new A.pT(),t.N)
p=A.x(p,p.$ti.h("z.E"))}if(p==null)p=B.P
q=q.a(a.j(0,"contributors"))
if(q==null)q=null
else{q=J.aU(q,new A.pU(),t.l4)
q=A.x(q,q.$ti.h("z.E"))}return new A.eM(s,r,p,q==null?B.du:q)},
cE:function cE(a,b,c){this.a=a
this.b=b
this.c=c},
eM:function eM(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.d=d},
pT:function pT(){},
pU:function pU(){},
pV:function pV(){},
Ax(a){switch(a){case"bool":return B.db
case"int":return B.dc
case"double":return B.dd
case"string":return B.aR
case"enum":return B.de
default:return B.aR}},
Ay(a){var s,r=A.r(a.j(0,"key")),q=A.r(a.j(0,"label")),p=A.Ax(A.r(a.j(0,"type"))),o=a.j(0,"value"),n=t.g.a(a.j(0,"options"))
if(n==null)n=null
else{n=J.aU(n,new A.q8(),t.N)
n=A.x(n,n.$ti.h("z.E"))}if(n==null)n=B.P
s=A.aA(a.j(0,"doc"))
return new A.aR(r,q,p,o,n,s==null?"":s)},
dR:function dR(a,b){this.a=a
this.b=b},
aR:function aR(a,b,c,d,e,f){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f},
q8:function q8(){},
f6:function f6(a,b){var _=this
_.a=a
_.b=b
_.d=_.c=0},
EJ(a){var s,r,q=a.j(0,"scopes")
if(t._.b(q)){s=J.aU(q,new A.rf(),t.N)
r=A.x(s,s.$ti.h("z.E"))}else r=B.P
return new A.hK(A.r(a.j(0,"role")),r)},
hK:function hK(a,b){this.a=a
this.b=b},
rf:function rf(){},
ER(a){var s,r,q,p=t._.a(a.j(0,"history")),o=A.at(a.j(0,"value")),n=A.r(a.j(0,"id")),m=A.r(a.j(0,"name")),l=A.r(a.j(0,"suffix")),k=A.aA(a.j(0,"display"))
if(k==null)k=B.e.k(o)
s=A.at(a.j(0,"min"))
r=A.at(a.j(0,"max"))
q=J.aU(p,new A.rw(),t.r)
q=A.x(q,q.$ti.h("z.E"))
return new A.kS(n,m,l,o,k,s,r,q)},
kS:function kS(a,b,c,d,e,f,g,h){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=g
_.w=h},
rw:function rw(){},
B0(a){var s=A.r(a.j(0,"id")),r=A.r(a.j(0,"label")),q=A.r(a.j(0,"host")),p=A.bb(a.j(0,"port")),o=A.r(a.j(0,"bearer")),n=A.x8(a.j(0,"secure"))
return new A.bq(s,r,q,p,o,n===!0,A.aA(a.j(0,"relayUrl")),A.aA(a.j(0,"serverPubKey")),A.aA(a.j(0,"fingerprint")))},
bq:function bq(a,b,c,d,e,f,g,h,i){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=g
_.w=h
_.x=i},
B1(a){var s,r,q,p=A.t(t.N,t.e5)
for(s=J.aE(t._.a(a.j(0,"data"))),r=t.P;s.p();){q=A.ER(r.a(s.gu()))
p.i(0,q.a,q)}return new A.b9(p,new A.b6(Date.now(),0,!1),0)},
b9:function b9(a,b,c){this.a=a
this.b=b
this.c=c},
Ez(a){switch(a){case"NORMAL":return B.bj
case"PRESSURE":return B.iJ
case"PANIC":return B.iK
default:return B.bj}},
Bg(a){return new A.c2(A.r(a.j(0,"name")),A.Ez(A.r(a.j(0,"pressureMode"))),A.at(a.j(0,"budgetMs")),A.at(a.j(0,"panicMs")),A.at(a.j(0,"releaseMs")))},
hG:function hG(a,b){this.a=a
this.b=b},
c2:function c2(a,b,c,d,e){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e},
j0:function j0(a,b,c,d,e,f,g,h,i){var _=this
_.d=a
_.e=b
_.f=c
_.r=d
_.w=e
_.x=f
_.y=g
_.z=h
_.a=i},
ny:function ny(a,b){this.a=a
this.b=b},
nz:function nz(a,b,c){this.a=a
this.b=b
this.c=c},
nA:function nA(a,b){this.a=a
this.b=b},
nB:function nB(a){this.a=a},
er:function er(a){this.a=a},
lL:function lL(a){var _=this
_.e=_.d=null
_.f=!1
_.r=null
_.w=a
_.x=0
_.c=_.a=null},
tB:function tB(a){this.a=a},
tA:function tA(a){this.a=a},
tC:function tC(){},
tx:function tx(a){this.a=a},
ty:function ty(a){this.a=a},
tw:function tw(a,b){this.a=a
this.b=b},
tz:function tz(a){this.a=a},
tv:function tv(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.d=d},
AG(a){var s,r,q=B.a.aG(a),p=$.CT().mv(q)
if(p!=null){s=p.b
if(0>=s.length)return A.f(s,0)
s=s[0]
s.toString
r=A.ar("\\s+",!0)
return A.d8(s,r,"")}s=A.ar("\\s+",!0)
return A.d8(q,s,"")},
ky(a7){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e,d,c,b,a,a0,a1,a2,a3,a4,a5=null,a6=A.AG(a7)
if(!B.a.M(a6,"RCT1."))return a5
s=B.a.S(a6,5)
try{r=B.l.a7(B.cg.aE(B.c9.cn(s)))
q=t.P.a(B.k.aJ(r,a5))
p=J.be(q,"host")
o=J.be(q,"port")
n=J.be(q,"tokenId")
m=J.be(q,"tokenSig")
l=J.be(q,"confirmWord")
k=J.be(q,"relayUrl")
j=J.be(q,"serverPubKey")
i=J.be(q,"fingerprint")
if(p!=null&&typeof p!="string")return a5
if(o!=null&&!A.mV(o))return a5
if(typeof n!="string"||typeof m!="string"||typeof l!="string")return a5
if(k!=null&&typeof k!="string")return a5
if(j!=null&&typeof j!="string")return a5
if(i!=null&&typeof i!="string")return a5
a=A.aA(p)
h=a==null?"":a
a0=A.BU(o)
g=a0==null?0:a0
f=A.aA(k)
e=A.aA(j)
d=A.aA(i)
if(J.b4(h)!==0){a1=g
if(typeof a1!=="number")return a1.al()
a2=a1>0}else a2=!1
c=a2
a1=f
a1=a1==null?a5:J.b4(a1)!==0
if(a1===!0){a1=e
a1=a1==null?a5:J.b4(a1)!==0
a3=a1===!0}else a3=!1
b=a3
if(!c&&!b)return a5
return new A.qG(h,g,n,m,l,f,e,d)}catch(a4){return a5}},
AH(a){var s,r=A.AG(a)
if(r.length===0)return"Paste the full RCT1 pairing code."
if(!B.a.M(r,"RCT1."))return"Pairing codes must start with RCT1."
s=B.a.S(r,5).length
if(s===0)return"The RCT1 payload is missing."
if(B.c.bX(s,4)===1)return"This code is incomplete. Copy the entire Pairing code line from the server console."
if(A.ky(r)==null)return"This RCT1 code could not be decoded. Copy the full code without truncating it."
return null},
nD(a,b){var s=0,r=A.Q(t.C),q,p,o,n,m,l,k
var $async$nD=A.R(function(c,d){if(c===1)return A.N(d,r)
for(;;)switch(s){case 0:k=A.ky(a)
if(k==null)throw A.d(A.dH(a,"code","Invalid RCT1 pairing code"))
p=B.c.dH(1000*Date.now(),36)
o=k.a
if(o.length!==0)n=o
else{m=k.w
if(m==null)m=k.f
n=m==null?"server":m}l=new A.bq(p,n,o,k.b,k.c+"."+k.d,!1,k.f,k.r,k.w)
s=3
return A.G(b.m(0,l),$async$nD)
case 3:q=l
s=1
break
case 1:return A.O(q,r)}})
return A.P($async$nD,r)},
qG:function qG(a,b,c,d,e,f,g,h){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=g
_.w=h},
es:function es(a,b){this.d=a
this.a=b},
i0:function i0(){var _=this
_.d=""
_.r=_.f=_.e=null
_.x=_.w=!1
_.c=_.a=null},
tE:function tE(a,b,c){this.a=a
this.b=b
this.c=c},
tD:function tD(a){this.a=a},
tK:function tK(a){this.a=a},
tL:function tL(a){this.a=a},
tF:function tF(a,b,c){this.a=a
this.b=b
this.c=c},
tG:function tG(a,b){this.a=a
this.b=b},
tH:function tH(a){this.a=a},
tI:function tI(a){this.a=a},
tJ:function tJ(a){this.a=a},
tM:function tM(a){this.a=a},
F9(a){var s
switch(a.a){case 2:s=A.nP("critical")
break
case 1:s=A.nQ("warning")
break
case 0:s=A.yr("info")
break
default:s=null}return s},
F8(a){var s=new A.b6(Date.now(),0,!1).cd(a).a,r=B.c.ag(s,1e6)
if(r<60)return""+r+"s ago"
r=B.c.ag(s,6e7)
if(r<60)return""+r+"m ago"
s=B.c.ag(s,36e8)
if(s<24)return""+s+"h ago"
return B.a.q(a.ng(),0,16)},
db:function db(a){this.a=a},
lO:function lO(){var _=this
_.c=_.a=_.e=_.d=null},
tU:function tU(){},
tV:function tV(a){this.a=a},
tW:function tW(){},
tX:function tX(){},
tY:function tY(a,b,c){this.a=a
this.b=b
this.c=c},
tT:function tT(a,b){this.a=a
this.b=b},
tZ:function tZ(a,b,c){this.a=a
this.b=b
this.c=c},
tS:function tS(a,b){this.a=a
this.b=b},
tQ:function tQ(a){this.a=a},
tP:function tP(a,b){this.a=a
this.b=b},
tN:function tN(a){this.a=a},
tR:function tR(a){this.a=a},
tO:function tO(a,b){this.a=a
this.b=b},
lN:function lN(a,b,c,d,e){var _=this
_.d=a
_.e=b
_.f=c
_.r=d
_.a=e},
jt:function jt(a){this.a=a},
dd:function dd(a){this.a=a},
lY:function lY(){var _=this
_.d="ticks-per-second"
_.c=_.a=_.e=null},
uj:function uj(){},
uk:function uk(a){this.a=a},
ul:function ul(a,b){this.a=a
this.b=b},
um:function um(a){this.a=a},
un:function un(){},
uo:function uo(){},
uf:function uf(){},
ug:function ug(a){this.a=a},
ue:function ue(a,b){this.a=a
this.b=b},
ui:function ui(a,b,c){this.a=a
this.b=b
this.c=c},
uh:function uh(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.d=d},
jA:function jA(a,b,c,d,e,f,g,h){var _=this
_.d=a
_.e=b
_.f=c
_.r=d
_.w=e
_.x=f
_.y=g
_.a=h},
ok:function ok(a,b){this.a=a
this.b=b},
ol:function ol(a,b){this.a=a
this.b=b},
eA:function eA(a){this.a=a},
i8:function i8(){var _=this
_.d=null
_.e=!1
_.c=_.a=null},
uq:function uq(a){this.a=a},
up:function up(){},
ur:function ur(){},
jK:function jK(a){this.a=a},
GO(a){var s
if(a==="cpu"||a==="jvm")return a.toUpperCase()
s=a.length
if(s===0)return a
if(0>=s)return A.f(a,0)
return a[0].toUpperCase()+B.a.S(a,1)},
Gm(a){var s,r=Math.abs(a),q=0
for(;;){if(!(r>=1024&&q<5))break
r/=1024;++q}if(q===0)s=B.e.Z(r,0)
else s=B.e.Z(r,r>=100?0:1)
if(!(q<6))return A.f(B.aZ,q)
return s+" "+B.aZ[q]},
GE(a,b){var s,r=A.d8(b.toLowerCase()," ","")
if(B.a.a8(r,"mb")||B.a.a8(r,"kb")||B.a.a8(r,"gb")||B.a.a8(r,"tb")||B.a.v(r,"percent")||B.a.v(r,"pct"))return!1
if(B.a.v(r,"byte"))return!0
s=a.toLowerCase()
if(!(B.a.v(s,"mem")||B.a.v(s,"disk")||B.a.v(s,"storage")||B.a.v(s,"gpu")))return!1
if(B.a.M(r,"physical")||B.a.M(r,"virtual")||B.a.M(r,"swap"))return!0
if(B.a.v(r,"vram"))return!0
return B.a.a8(r,"total")||B.a.a8(r,"free")||B.a.a8(r,"used")||B.a.a8(r,"size")||B.a.a8(r,"capacity")},
Gg(a,b,c){var s,r
if(c==null)return""
if(typeof c=="number"&&A.GE(a,b))return A.Gm(c)
s=J.aF(c)
r=$.Dc()
return A.d8(s,r,"")},
jL:function jL(a,b){this.d=a
this.a=b},
eE:function eE(a){this.a=a},
ic:function ic(){var _=this
_.d=null
_.f=_.e=!1
_.c=_.a=null},
uA:function uA(a){this.a=a},
uz:function uz(a,b){this.a=a
this.b=b},
uB:function uB(a){this.a=a},
uy:function uy(a){this.a=a},
uv:function uv(a){this.a=a},
uw:function uw(a){this.a=a},
uu:function uu(a,b){this.a=a
this.b=b},
ux:function ux(a){this.a=a},
ut:function ut(a){this.a=a},
jP:function jP(a){this.a=a},
FC(a){var s
switch(a.r.a){case 0:s=B.j5
break
case 1:s=B.bp
break
case 2:s=B.bm
break
case 3:s=B.bo
break
default:s=null}return s},
FB(a){var s,r
if(a==null)return"Never"
s=new A.b6(Date.now(),0,!1).cd(a).a
r=B.c.ag(s,1e6)
if(r<60)return""+r+"s ago"
r=B.c.ag(s,6e7)
if(r<60)return""+r+"m ago"
r=B.c.ag(s,36e8)
if(r<24)return""+r+"h ago"
return""+B.c.ag(s,864e8)+"d ago"},
Fr(a){var s=a.c
if(s===B.w)return B.bo
if(s===B.B)return B.bn
s=a.r
if(s===B.aL)return B.bm
if(s===B.aK)return B.bp
s=a.w
if(s>0)return new A.A(""+s+" alert(s)",B.t)
return B.bn},
eG:function eG(a){this.a=a},
mc:function mc(){this.d="All"
this.c=this.a=null},
uL:function uL(a){this.a=a},
uM:function uM(){},
uJ:function uJ(){},
uK:function uK(a){this.a=a},
uI:function uI(a,b){this.a=a
this.b=b},
mF:function mF(a,b){this.d=a
this.a=b},
vW:function vW(a,b){this.a=a
this.b=b},
mu:function mu(a,b){this.d=a
this.a=b},
hg:function hg(a,b,c,d,e,f){var _=this
_.d=a
_.e=b
_.f=c
_.r=d
_.w=e
_.a=f},
pr:function pr(a,b){this.a=a
this.b=b},
eH:function eH(a){this.a=a},
mh:function mh(){var _=this
_.e=_.d=null
_.f=!1
_.c=_.a=null},
va:function va(a){this.a=a},
v9:function v9(){},
vb:function vb(){},
v8:function v8(){},
xp(a,b){return A.GM(a,b)},
GM(a,b){var s=0,r=A.Q(t.gR),q,p=2,o=[],n,m,l
var $async$xp=A.R(function(c,d){if(c===1){o.push(d)
s=p}for(;;)switch(s){case 0:p=4
s=7
return A.G(a.dn(b),$async$xp)
case 7:n=d
q=n
s=1
break
p=2
s=6
break
case 4:p=3
l=o.pop()
if(A.a1(l) instanceof A.b8){q=null
s=1
break}else throw l
s=6
break
case 3:s=2
break
case 6:case 1:return A.O(q,r)
case 2:return A.N(o.at(-1),r)}})
return A.P($async$xp,r)},
nd(a){var s=0,r=A.Q(t.iM),q,p,o,n,m
var $async$nd=A.R(function(b,c){if(b===1)return A.N(c,r)
for(;;)switch(s){case 0:s=3
return A.G(a.dq(),$async$nd)
case 3:n=c
m=A.a([],t.n5)
p=J.aE(n)
case 4:if(!p.p()){s=5
break}s=6
return A.G(A.xp(a,p.gu().a),$async$nd)
case 6:o=c
if(o!=null)B.b.m(m,o)
s=4
break
case 5:q=m
s=1
break
case 1:return A.O(q,r)}})
return A.P($async$nd,r)},
eK:function eK(a){this.a=a},
mi:function mi(){var _=this
_.e=_.d=null
_.r=_.f=!1
_.c=_.a=null},
vf:function vf(a){this.a=a},
ve:function ve(a,b){this.a=a
this.b=b},
vg:function vg(a){this.a=a},
vd:function vd(a){this.a=a},
GS(a){var s=a.toUpperCase()
if(B.a.v(s,"CRIT")||B.a.v(s,"PANIC"))return A.nP(a)
if(B.a.v(s,"ELEV")||B.a.v(s,"WARN")||B.a.v(s,"PRESSURE"))return A.nQ(a)
return A.A7(a,!0)},
k0:function k0(a,b,c,d){var _=this
_.d=a
_.e=b
_.f=c
_.a=d},
lZ:function lZ(a,b){this.d=a
this.a=b},
eL:function eL(a){this.a=a},
ml:function ml(){var _=this
_.d=null
_.f=_.e=!1
_.c=_.a=null},
vl:function vl(a){this.a=a},
vk:function vk(a,b){this.a=a
this.b=b},
vm:function vm(a){this.a=a},
vj:function vj(a){this.a=a},
k1:function k1(a){this.a=a},
z9(a,b){var s,r,q
if(a==null)return!1
for(s=b.length,r=a.a,q=0;q<s;++q)if(r.K(b[q]))return!0
return!1},
zd(a){return new A.E(A.a(B.a.dD(B.a.dD(B.a.dD(a,"adapt-",""),"iris-",""),"wormholes-","").split("-"),t.s),t.d1.a(new A.xl()),t.gQ).aA(0," ")},
k5:function k5(a){this.a=a},
lM:function lM(a,b){this.d=a
this.a=b},
mn:function mn(a,b){this.d=a
this.a=b},
mS:function mS(a,b){this.d=a
this.a=b},
xl:function xl(){},
k7:function k7(a){this.a=a},
kg:function kg(a,b,c,d,e,f,g){var _=this
_.d=a
_.e=b
_.f=c
_.r=d
_.w=e
_.x=f
_.a=g},
qk:function qk(a){this.a=a},
ql:function ql(a){this.a=a},
qm:function qm(a){this.a=a},
eT:function eT(a){this.a=a},
ms:function ms(){var _=this
_.e=_.d=null
_.f=!1
_.c=_.a=null},
vD:function vD(a){this.a=a},
vC:function vC(){},
vz:function vz(a,b){this.a=a
this.b=b},
vy:function vy(a,b){this.a=a
this.b=b},
vA:function vA(a,b){this.a=a
this.b=b},
vx:function vx(a){this.a=a},
vB:function vB(a,b){this.a=a
this.b=b},
vw:function vw(a,b){this.a=a
this.b=b},
kh:function kh(a){this.a=a},
ki:function ki(a){this.a=a},
mg:function mg(a,b){this.d=a
this.a=b},
kv:function kv(a,b,c,d,e,f,g,h,i){var _=this
_.d=a
_.e=b
_.f=c
_.r=d
_.w=e
_.x=f
_.y=g
_.z=h
_.a=i},
qC:function qC(){},
qD:function qD(a){this.a=a},
qE:function qE(a){this.a=a},
qF:function qF(){},
m9:function m9(a,b,c,d,e){var _=this
_.d=a
_.e=b
_.f=c
_.r=d
_.a=e},
uD:function uD(a){this.a=a},
uE:function uE(a){this.a=a},
eZ:function eZ(a){this.a=a},
mw:function mw(){var _=this
_.e=_.d=null
_.f=!1
_.c=_.a=_.r=null},
vP:function vP(a){this.a=a},
vO:function vO(){},
vQ:function vQ(){},
vJ:function vJ(a){this.a=a},
vK:function vK(){},
vL:function vL(a){this.a=a},
vI:function vI(a,b){this.a=a
this.b=b},
vM:function vM(a,b){this.a=a
this.b=b},
vN:function vN(a){this.a=a},
vH:function vH(a){this.a=a},
kx:function kx(a){this.a=a},
mm:function mm(a,b){this.d=a
this.a=b},
mk:function mk(a,b,c,d){var _=this
_.d=a
_.e=b
_.f=c
_.a=d},
kB:function kB(a){this.a=a},
dp:function dp(a){this.a=a},
iA:function iA(a,b,c,d){var _=this
_.d=!1
_.z=_.y=_.x=_.w=_.r=_.f=_.e=""
_.Q=a
_.as=b
_.at=c
_.ax=d
_.ay=null
_.CW=_.ch=!1
_.c=_.a=_.cy=_.cx=null},
w2:function w2(a,b,c){this.a=a
this.b=b
this.c=c},
w5:function w5(a,b){this.a=a
this.b=b},
w1:function w1(){},
w3:function w3(a,b){this.a=a
this.b=b},
vZ:function vZ(a){this.a=a},
w_:function w_(a){this.a=a},
vX:function vX(a,b){this.a=a
this.b=b},
w4:function w4(){},
wE:function wE(a){this.a=a},
wF:function wF(a){this.a=a},
wC:function wC(a,b){this.a=a
this.b=b},
wD:function wD(a,b){this.a=a
this.b=b},
w0:function w0(a,b){this.a=a
this.b=b},
vY:function vY(a){this.a=a},
wt:function wt(a){this.a=a},
ws:function ws(a,b){this.a=a
this.b=b},
wu:function wu(a){this.a=a},
wr:function wr(a,b){this.a=a
this.b=b},
wv:function wv(a){this.a=a},
wq:function wq(a,b){this.a=a
this.b=b},
ww:function ww(a){this.a=a},
wp:function wp(a,b){this.a=a
this.b=b},
wx:function wx(a){this.a=a},
wo:function wo(a,b){this.a=a
this.b=b},
wy:function wy(a){this.a=a},
wn:function wn(a,b){this.a=a
this.b=b},
wz:function wz(a){this.a=a},
wm:function wm(a,b){this.a=a
this.b=b},
wA:function wA(a,b){this.a=a
this.b=b},
wB:function wB(a,b){this.a=a
this.b=b},
wh:function wh(a,b){this.a=a
this.b=b},
wi:function wi(a,b){this.a=a
this.b=b},
wj:function wj(a){this.a=a},
wk:function wk(a,b){this.a=a
this.b=b},
wa:function wa(a,b){this.a=a
this.b=b},
w9:function w9(a,b,c){this.a=a
this.b=b
this.c=c},
wb:function wb(a,b,c){this.a=a
this.b=b
this.c=c},
wc:function wc(a,b){this.a=a
this.b=b},
w8:function w8(a,b){this.a=a
this.b=b},
we:function we(a,b,c){this.a=a
this.b=b
this.c=c},
wd:function wd(a){this.a=a},
w7:function w7(a){this.a=a},
wf:function wf(a,b){this.a=a
this.b=b},
w6:function w6(a,b,c){this.a=a
this.b=b
this.c=c},
wg:function wg(a,b,c){this.a=a
this.b=b
this.c=c},
wl:function wl(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.d=d},
lz:function lz(a,b,c,d,e){var _=this
_.d=a
_.e=b
_.f=c
_.r=d
_.a=e},
tc:function tc(a,b){this.a=a
this.b=b},
td:function td(a,b,c){this.a=a
this.b=b
this.c=c},
fg:function fg(a){this.a=a},
mM:function mM(){var _=this
_.e=_.d=null
_.f=!1
_.c=_.a=null},
wO:function wO(a){this.a=a},
wN:function wN(){},
wP:function wP(){},
lJ:function lJ(a,b,c,d,e){var _=this
_.d=a
_.e=b
_.f=c
_.r=d
_.a=e},
ts:function ts(a,b){this.a=a
this.b=b},
tt:function tt(a,b){this.a=a
this.b=b},
tu:function tu(a,b){this.a=a
this.b=b},
fi:function fi(a){this.a=a},
mR:function mR(){var _=this
_.e=_.d=null
_.f=!1
_.c=_.a=null},
x6:function x6(a){this.a=a},
x5:function x5(){},
x7:function x7(){},
lK:function lK(a){this.a=a},
dW:function dW(a,b){this.a=a
this.b=b},
jX:function jX(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.d=d},
ps:function ps(a,b){this.a=a
this.b=b},
pt:function pt(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.d=d},
Er(a){return new A.qx(a,new A.qy())},
qx:function qx(a,b){this.a=a
this.b=b},
qy:function qy(){},
EF(a,b){var s=new A.jl(A.a([],t.O))
return new A.ci(a,s,b)},
ci:function ci(a,b,c){this.a=a
this.b=b
this.c=c},
qM:function qM(){},
qP:function qP(){},
qO:function qO(){},
qR:function qR(){},
qN:function qN(){},
qQ:function qQ(){},
bM(a){return new A.b8(a)},
AV(a){return new A.f1(a)},
AW(a){return new A.f2(a)},
AU(a){return new A.f0(a)},
dm:function dm(a){this.a=a},
b8:function b8(a){this.a=a},
f1:function f1(a){this.a=a},
f2:function f2(a){this.a=a},
f0:function f0(a){this.a=a},
G_(a){var s=new A.mP(A.rZ(t.N))
s.j7(a)
return s},
mP:function mP(a){this.a=a
this.b=$
this.c=!1},
x_:function x_(a){this.a=a},
x0:function x0(a){this.a=a},
x1:function x1(a){this.a=a},
BR(a){var s=new A.mQ(A.rZ(t.c))
s.j8(a)
return s},
mQ:function mQ(a){this.a=a
this.b=$
this.c=!1},
x2:function x2(a){this.a=a},
x3:function x3(a){this.a=a},
x4:function x4(a){this.a=a},
fK:function fK(a,b,c){this.a=a
this.b=b
this.c=c},
nC:function nC(a,b,c,d,e){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=!1
_.r=e},
Ds(a,b,c,d,e){var s,r,q,p,o,n,m,l,k,j
if(d==null)return B.dq
s=A.a([],t.a2)
r=d.a
q=r.j(0,"ticks-per-second")
if(q!=null){p=q.d
o=e.b
if(p<o)B.b.m(s,A.et(!0,a,q,b,c,B.E,o,"Low TPS"))
else{o=e.a
if(p<o)B.b.m(s,A.et(!0,a,q,b,c,B.r,o,"Low TPS"))}}n=r.j(0,"tick-time")
if(n!=null&&n.d>e.c)B.b.m(s,A.et(!1,a,n,b,c,B.r,e.c,"High MSPT"))
m=r.j(0,"incident-score")
if(m!=null&&m.d>e.d)B.b.m(s,A.et(!1,a,m,b,c,B.r,e.d,"Elevated incident score"))
l=r.j(0,"gc-time-percent")
if(l!=null&&l.d>e.e)B.b.m(s,A.et(!1,a,l,b,c,B.r,e.e,"High GC time"))
k=r.j(0,"player-ping-p95")
if(k!=null&&k.d>e.f)B.b.m(s,A.et(!1,a,k,b,c,B.r,e.f,"High ping p95"))
j=r.j(0,"memory-pressure")
if(j!=null&&j.d>e.r)B.b.m(s,A.et(!1,a,j,b,c,B.r,e.r,"Memory pressure"))
return s},
yp(a,b,c){var s,r,q,p=A.a([],t.a2)
for(s=b.length,r=0;r<b.length;b.length===s||(0,A.I)(b),++r){q=b[r]
B.b.B(p,A.Ds(a,q.a,q.b,q.c,c))}B.b.ai(p,new A.nE())
return p},
et(a,b,c,d,e,f,g,h){var s,r=c.c,q=r.length===0?"":" "+r
r=A.w(g)
s=a?"(< "+r+")":"(> "+r+")"
return new A.bm(d,e,c.a,f,h,c.e+q+" "+s,c.d,g,b,b)},
nE:function nE(){},
nF:function nF(a,b,c){var _=this
_.a=a
_.d=_.c=_.b=$
_.e=b
_.f=c},
nH:function nH(a){this.a=a},
nI:function nI(a){this.a=a},
nJ:function nJ(){},
nG:function nG(){},
jz:function jz(a,b,c,d,e){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=!1
_.w=null},
Ak(a,b,c){return new A.h1(a,b,c,B.ab,A.rZ(t.c),A.rZ(t.x),A.t(t.N,t.bw))},
bU:function bU(a,b){this.a=a
this.b=b},
h1:function h1(a,b,c,d,e,f,g){var _=this
_.a=a
_.d=b
_.e=c
_.f=d
_.w=_.r=!1
_.y=_.x=0
_.z=!1
_.as=_.Q=null
_.at=e
_.ax=f
_.ay=g},
ot:function ot(a){this.a=a},
os:function os(){},
eB:function eB(a,b,c,d,e){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=!1},
oE:function oE(a){this.a=a},
oF:function oF(a){this.a=a},
oG:function oG(a){this.a=a},
oB:function oB(a){this.a=a},
oC:function oC(a){this.a=a},
oD:function oD(a){this.a=a},
h6:function h6(a,b,c){this.d=a
this.b=b
this.a=c},
eF:function eF(a,b){this.d=a
this.a=b},
mb:function mb(){this.d=-1
this.c=this.a=null},
uH:function uH(){},
H3(a){var s=Date.now(),r=A.F(a),q=r.h("E<1,L<b,@>>")
r=A.x(new A.E(a,r.h("L<b,@>(1)").a(new A.xs()),q),q.h("z.E"))
return B.k.bb(A.j(["kind","reactor-fleet","version",1,"exportedAt",s,"servers",r],t.N,t.K),null)},
I8(a){var s,r,q,p,o,n,m,l,k=null
try{k=B.k.aJ(a,null)}catch(p){return B.cJ}o=t.P
if(!o.b(k))return B.cI
if(!J.a8(k.j(0,"kind"),"reactor-fleet"))return B.cL
n=k.j(0,"servers")
if(!t._.b(n))return B.cK
s=A.a([],t.cy)
r=0
for(m=J.aE(n);m.p();){q=m.gu()
if(!o.b(q)){l=r
if(typeof l!=="number")return l.fj()
r=l+1
continue}try{J.fJ(s,A.B0(q))}catch(p){l=r
if(typeof l!=="number")return l.fj()
r=l+1}}if(J.b4(s)===0){o=r
m=r
if(typeof m!=="number")return m.al()
if(m>0){m=A.w(r)
l=J.a8(r,1)?"entry":"entries"
l="No valid servers found ("+m+" malformed "+l+")"
m=l}else m="No servers in file"
return new A.dh(B.Q,o,m)}return new A.dh(s,r,null)},
dh:function dh(a,b,c){this.a=a
this.b=b
this.c=c},
xs:function xs(){},
Ib(a){var s=A.p(A.p(v.G.document).createElement("input"))
s.type="file"
s.accept=".json,application/json"
s.addEventListener("change",A.dA(new A.yb(s,a)))
s.click()},
yb:function yb(a,b){this.a=a
this.b=b},
ya:function ya(a,b){this.a=a
this.b=b},
bX:function bX(a,b,c,d,e){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e},
iy:function iy(a,b,c){var _=this
_.a=a
_.c=_.b=null
_.d=b
_.e=c
_.r=_.f=null},
oX:function oX(a,b,c){var _=this
_.a=a
_.b=b
_.c=c
_.d=!1},
p_:function p_(a){this.a=a},
p0:function p0(a){this.a=a},
p1:function p1(){},
p2:function p2(a){this.a=a},
p3:function p3(a){this.a=a},
oY:function oY(a,b){this.a=a
this.b=b},
oZ:function oZ(a,b){this.a=a
this.b=b},
hc:function hc(a,b,c,d){var _=this
_.d=a
_.e=b
_.b=c
_.a=d},
dN:function dN(a,b,c){this.d=a
this.e=b
this.a=c},
md:function md(){var _=this
_.d=$
_.e=0
_.c=_.a=null},
uO:function uO(a){this.a=a},
uN:function uN(a){this.a=a},
DU(a,b,c){var s=t.N
s=new A.p4(c,a,b,A.a([],t.cy),A.t(s,t.mN),A.t(s,t.lF))
s.lz()
return s},
p4:function p4(a,b,c,d,e,f){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=null},
p9:function p9(a){this.a=a},
p8:function p8(a){this.a=a},
p7:function p7(a){this.a=a},
p5:function p5(a){this.a=a},
p6:function p6(){},
DV(b0,b1){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e,d,c,b,a,a0,a1,a2,a3,a4,a5,a6,a7,a8,a9=null
if(b1.length===0)return new A.jS(0,0,0,0,0,A.j([B.E,0,B.r,0,B.X,0],t.eV,t.S),B.aW,B.aW)
s=A.j([B.E,0,B.r,0,B.X,0],t.eV,t.S)
for(r=b0.length,q=0;q<b0.length;b0.length===r||(0,A.I)(b0),++q){p=b0[q].d
o=s.j(0,p)
s.i(0,p,(o==null?0:o)+1)}n=A.a([],t.k6)
for(r=b1.length,p=A.F(b0),o=p.h("y(1)"),p=p.h("a3<1>"),m=0,l=0,k=0,j=1/0,i=0,h=0,q=0;g=b1.length,q<g;b1.length===r||(0,A.I)(b1),++q){f=b1[q]
e=f.d
g=e==null
if(g)d=a9
else{c=e.a.j(0,"ticks-per-second")
d=c==null?a9:c.d}if(g)b=a9
else{c=e.a.j(0,"tick-time")
b=c==null?a9:c.d}if(g)c=a9
else{c=e.a.j(0,"players")
c=c==null?a9:c.d}a=B.e.ac(c==null?0:c)
if(g)a0=a9
else{g=e.a.j(0,"incident-score")
g=g==null?a9:g.d
a0=g}if(a0==null)a0=0
g=f.c
c=g===B.w
if(c)a1=0
else{a2=B.e.a3((d==null?0:d)/20,0,1)*100
if((b==null?0:b)>50)a2-=20
a1=B.e.ac(B.e.a3(a0>50?a2-20:a2,0,100))}if(c)a3=B.cH
else if(a1<=50)a3=B.aL
else a3=a1<80?B.aK:B.aJ
a4=new A.a3(b0,o.a(new A.pa(f)),p).gn(0)
if(d!=null){l+=d;++k
if(d<j)j=d}if(b!=null&&b>i)i=b
h+=a
m+=a1
B.b.m(n,new A.bx(f.a,f.b,g,d,a,a3,a4,f.e))}r=k>0
a5=r?l/k:0
if(r)a6=j===1/0?0:j
else a6=0
a7=B.e.ac(m/g)
r=t.el
a8=A.x(new A.a3(n,t.nx.a(new A.pb()),r),r.h("m.E"))
B.b.ai(a8,new A.pc(new A.pd()))
return new A.jS(a5,a6,h,i,a7,s,n,a8)},
aV:function aV(a,b,c,d,e){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e},
dg:function dg(a,b){this.a=a
this.b=b},
bx:function bx(a,b,c,d,e,f,g,h){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.f=e
_.r=f
_.w=g
_.x=h},
jS:function jS(a,b,c,d,e,f,g,h){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=g
_.w=h},
pa:function pa(a){this.a=a},
pd:function pd(){},
pb:function pb(){},
pc:function pc(a){this.a=a},
cA(a){var s=a.H(t.ne)
return s==null?null:s.d},
hd:function hd(a,b,c,d){var _=this
_.d=a
_.e=b
_.b=c
_.a=d},
hi:function hi(a,b,c){this.d=a
this.b=b
this.a=c},
qh:function qh(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.e=d
_.f=!1
_.r="ALL"
_.x=!1
_.y=null},
qj:function qj(a){this.a=a},
qi:function qi(a){this.a=a},
hD:function hD(a,b,c,d){var _=this
_.d=a
_.e=b
_.b=c
_.a=d},
hL:function hL(a,b,c){this.d=a
this.b=b
this.a=c},
dX:function dX(a,b,c){this.d=a
this.e=b
this.a=c},
mz:function mz(){var _=this
_.d=null
_.e=!1
_.c=_.a=null},
vT:function vT(a,b){this.a=a
this.b=b},
mU:function mU(){},
hO:function hO(a,b,c,d){var _=this
_.d=a
_.e=b
_.b=c
_.a=d},
rD:function rD(a){this.a=a
this.b=null},
rE:function rE(){},
rH:function rH(){},
rF:function rF(a){this.a=a},
rG:function rG(){},
lG:function lG(){},
lI:function lI(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=!1},
tp:function tp(a){this.a=a},
tq:function tq(a){this.a=a},
tr:function tr(a){this.a=a},
iW(a){var s
switch(a.a){case 0:s="var(--success)"
break
case 1:s="var(--warning)"
break
case 2:s="var(--destructive)"
break
case 3:s="var(--info)"
break
case 4:s="var(--muted-foreground)"
break
default:s=null}return s},
iV(a,b){var s
switch(b.a){case 0:s=A.A7(a,!1)
break
case 1:s=A.nQ(a)
break
case 2:s=A.nP(a)
break
case 3:s=A.yr(a)
break
case 4:s=A.A6(a)
break
default:s=null}return s},
dC(a,b,c){var s=""+c+"px",r=t.N
s=A.B(A.j(["display","inline-block","width",s,"height",s,"border-radius","999px","background",A.iW(a),"box-shadow","0 0 0 3px "+("color-mix(in srgb, "+A.iW(a)+" 18%, transparent)"),"flex","0 0 auto"],r,r))
return A.H(B.n,b==null?null:A.j(["aria-label",b,"title",b,"role","img"],r,r),null,null,s)},
aK(a,b){var s=null,r=t.N
return new A.c(s,"reactor-grid",A.B(A.j(["display","grid","grid-template-columns","repeat(auto-fill, minmax("+b+", 1fr))","gap","1rem"],r,r)),s,s,a,s)},
a2(a,b,c,d,e){return new A.kI(e,d,a,c,b,null)},
J(a,b,c,d,e,f){return new A.e0(e,c,f,a,b,d,null)},
AX(a,b,c){return new A.kL(a,c,b,null)},
dV:function dV(a,b){this.a=a
this.b=b},
kI:function kI(a,b,c,d,e,f){var _=this
_.d=a
_.e=b
_.f=c
_.r=d
_.w=e
_.a=f},
kJ:function kJ(a,b,c,d,e){var _=this
_.d=a
_.e=b
_.f=c
_.r=d
_.a=e},
e0:function e0(a,b,c,d,e,f,g){var _=this
_.d=a
_.e=b
_.f=c
_.r=d
_.w=e
_.y=f
_.a=g},
kj:function kj(a,b,c,d,e){var _=this
_.d=a
_.e=b
_.w=c
_.x=d
_.a=e},
kL:function kL(a,b,c,d){var _=this
_.d=a
_.e=b
_.f=c
_.a=d},
kK:function kK(a,b,c,d){var _=this
_.d=a
_.e=b
_.f=c
_.a=d},
jC:function jC(a,b,c,d){var _=this
_.d=a
_.e=b
_.f=c
_.a=d},
oo:function oo(a,b){this.a=a
this.b=b},
jB:function jB(a,b,c,d,e,f){var _=this
_.d=a
_.e=b
_.f=c
_.r=d
_.w=e
_.a=f},
cb(a,b,c,d,e,f){return new A.jW(c,f,a,d,e,b,null)},
yD(a,b){if(a>=b.b)return B.cS
if(a>=b.a)return B.cR
return B.aO},
DZ(a){var s
switch(a.a){case 0:s="var(--success)"
break
case 1:s="var(--warning)"
break
case 2:s="var(--destructive)"
break
default:s=null}return s},
E_(a){var s
switch(a.a){case 0:s="Nominal"
break
case 1:s="Elevated"
break
case 2:s="Critical"
break
default:s=null}return s},
hf:function hf(a,b){this.a=a
this.b=b},
jW:function jW(a,b,c,d,e,f,g){var _=this
_.d=a
_.e=b
_.f=c
_.r=d
_.w=e
_.x=f
_.a=g},
jY:function jY(a,b){this.d=a
this.a=b},
dQ:function dQ(a,b,c,d){var _=this
_.d=a
_.e=b
_.f=c
_.a=d},
q3:function q3(a){this.a=a},
q4:function q4(a){this.a=a},
q5:function q5(a){this.a=a},
q6:function q6(a){this.a=a},
q7:function q7(a){this.a=a},
cO:function cO(a,b){this.d=a
this.a=b},
Y(a,b){return new A.dr(a,b,null)},
EX(a){if(a==null)return"--"
return a.e},
dr:function dr(a,b,c){this.d=a
this.e=b
this.a=c},
B7(a){var s
switch(a.a){case 1:s="Live"
break
case 0:s="Connecting"
break
case 2:s="Degraded"
break
case 3:s="Offline"
break
default:s=null}return s},
EY(a){var s
switch(a.a){case 1:s=B.C
break
case 0:s=B.D
break
case 2:s=B.t
break
case 3:s=B.K
break
default:s=null}return s},
fc:function fc(a,b){this.d=a
this.a=b},
yB(a,b){if(b<0)A.ak(A.b1("Offset may not be negative, was "+b+"."))
else if(b>a.c.length)A.ak(A.b1("Offset "+b+u.s+a.gn(0)+"."))
return new A.jR(a,b)},
rU:function rU(a,b,c){var _=this
_.a=a
_.b=b
_.c=c
_.d=null},
jR:function jR(a,b){this.a=a
this.b=b},
fn:function fn(a,b,c){this.a=a
this.b=b
this.c=c},
E1(a,b){var s=A.E2(A.a([A.Fj(a,!0)],t.pg)),r=new A.pP(b).$0(),q=B.c.k(B.b.gaL(s).b+1),p=A.E3(s)?0:3,o=A.F(s)
return new A.pv(s,r,null,1+Math.max(q.length,p),new A.E(s,o.h("h(1)").a(new A.px()),o.h("E<1,h>")).n3(0,B.cf),!A.I0(new A.E(s,o.h("u?(1)").a(new A.py()),o.h("E<1,u?>"))),new A.aI(""))},
E3(a){var s,r,q
for(s=0;s<a.length-1;){r=a[s];++s
q=a[s]
if(r.b+1!==q.b&&J.a8(r.c,q.c))return!1}return!0},
E2(a){var s,r,q=A.Hs(a,new A.pA(),t.D,t.K)
for(s=A.n(q),r=new A.bh(q,q.r,q.e,s.h("bh<2>"));r.p();)J.zD(r.d,new A.pB())
s=s.h("aC<1,2>")
r=s.h("ha<m.E,bF>")
s=A.x(new A.ha(new A.aC(q,s),s.h("m<bF>(m.E)").a(new A.pC()),r),r.h("m.E"))
return s},
Fj(a,b){var s=new A.vh(a).$0()
return new A.aS(s,!0,null)},
Fl(a){var s,r,q,p,o,n,m=a.gad()
if(!B.a.v(m,"\r\n"))return a
s=a.gF().ga6()
for(r=m.length-1,q=0;q<r;++q)if(m.charCodeAt(q)===13&&m.charCodeAt(q+1)===10)--s
r=a.gG()
p=a.gR()
o=a.gF().gY()
p=A.lc(s,a.gF().ga4(),o,p)
o=A.d8(m,"\r\n","\n")
n=a.gao()
return A.rV(r,p,o,A.d8(n,"\r\n","\n"))},
Fm(a){var s,r,q,p,o,n,m
if(!B.a.a8(a.gao(),"\n"))return a
if(B.a.a8(a.gad(),"\n\n"))return a
s=B.a.q(a.gao(),0,a.gao().length-1)
r=a.gad()
q=a.gG()
p=a.gF()
if(B.a.a8(a.gad(),"\n")){o=A.xZ(a.gao(),a.gad(),a.gG().ga4())
o.toString
o=o+a.gG().ga4()+a.gn(a)===a.gao().length}else o=!1
if(o){r=B.a.q(a.gad(),0,a.gad().length-1)
if(r.length===0)p=q
else{o=a.gF().ga6()
n=a.gR()
m=a.gF().gY()
p=A.lc(o-1,A.Bp(s),m-1,n)
q=a.gG().ga6()===a.gF().ga6()?p:a.gG()}}return A.rV(q,p,r,s)},
Fk(a){var s,r,q,p,o
if(a.gF().ga4()!==0)return a
if(a.gF().gY()===a.gG().gY())return a
s=B.a.q(a.gad(),0,a.gad().length-1)
r=a.gG()
q=a.gF().ga6()
p=a.gR()
o=a.gF().gY()
p=A.lc(q-1,s.length-B.a.eX(s,"\n")-1,o-1,p)
return A.rV(r,p,s,B.a.a8(a.gao(),"\n")?B.a.q(a.gao(),0,a.gao().length-1):a.gao())},
Bp(a){var s,r=a.length
if(r===0)return 0
else{s=r-1
if(!(s>=0))return A.f(a,s)
if(a.charCodeAt(s)===10)return r===1?0:r-B.a.dt(a,"\n",r-2)-1
else return r-B.a.eX(a,"\n")-1}},
pv:function pv(a,b,c,d,e,f,g){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=g},
pP:function pP(a){this.a=a},
px:function px(){},
pw:function pw(){},
py:function py(){},
pA:function pA(){},
pB:function pB(){},
pC:function pC(){},
pz:function pz(a){this.a=a},
pQ:function pQ(){},
pD:function pD(a){this.a=a},
pK:function pK(a,b,c){this.a=a
this.b=b
this.c=c},
pL:function pL(a,b){this.a=a
this.b=b},
pM:function pM(a){this.a=a},
pN:function pN(a,b,c,d,e,f,g){var _=this
_.a=a
_.b=b
_.c=c
_.d=d
_.e=e
_.f=f
_.r=g},
pI:function pI(a,b){this.a=a
this.b=b},
pJ:function pJ(a,b){this.a=a
this.b=b},
pE:function pE(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.d=d},
pF:function pF(a,b,c){this.a=a
this.b=b
this.c=c},
pG:function pG(a,b,c){this.a=a
this.b=b
this.c=c},
pH:function pH(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.d=d},
pO:function pO(a,b,c){this.a=a
this.b=b
this.c=c},
aS:function aS(a,b,c){this.a=a
this.b=b
this.c=c},
vh:function vh(a){this.a=a},
bF:function bF(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.d=d},
lc(a,b,c,d){if(a<0)A.ak(A.b1("Offset may not be negative, was "+a+"."))
else if(c<0)A.ak(A.b1("Line may not be negative, was "+c+"."))
else if(b<0)A.ak(A.b1("Column may not be negative, was "+b+"."))
return new A.c1(d,a,c,b)},
c1:function c1(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.d=d},
ld:function ld(){},
le:function le(){},
EV(a,b,c){return new A.fa(c,a,b)},
lf:function lf(){},
fa:function fa(a,b,c){this.c=a
this.a=b
this.b=c},
fb:function fb(){},
rV(a,b,c,d){var s=new A.cT(d,a,b,c)
s.j4(a,b,c)
if(!B.a.v(d,c))A.ak(A.ai('The context line "'+d+'" must contain "'+c+'".',null))
if(A.xZ(d,c,a.ga4())==null)A.ak(A.ai('The span text "'+c+'" must start at column '+(a.ga4()+1)+' in a line within "'+d+'".',null))
return s},
cT:function cT(a,b,c,d){var _=this
_.d=a
_.a=b
_.b=c
_.c=d},
lm:function lm(a,b,c){this.c=a
this.a=b
this.b=c},
t1:function t1(a,b){var _=this
_.a=a
_.b=b
_.c=0
_.e=_.d=null},
yZ(a,b,c,d,e){var s=A.GY(new A.uC(c),t.m)
s=s==null?null:A.dA(s)
if(s!=null)a.addEventListener(b,s,!1)
return new A.ie(a,b,s,!1,e.h("ie<0>"))},
GY(a,b){var s=$.a0
if(s===B.m)return a
return s.lU(a,b)},
yA:function yA(a,b){this.a=a
this.$ti=b},
id:function id(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.$ti=d},
m6:function m6(a,b,c,d){var _=this
_.a=a
_.b=b
_.c=c
_.$ti=d},
ie:function ie(a,b,c,d,e){var _=this
_.b=a
_.c=b
_.d=c
_.e=d
_.$ti=e},
uC:function uC(a){this.a=a},
Ic(a){if(typeof dartPrint=="function"){dartPrint(a)
return}if(typeof console=="object"&&typeof console.log!="undefined"){console.log(a)
return}if(typeof print=="function"){print(a)
return}throw"Unable to print message: "+String(a)},
CH(a,b,c){A.Cr(c,t.cZ,"T","max")
return Math.max(c.a(a),c.a(b))},
zm(a){var s,r
if(a==null)return B.x
s=A.Hj(A.a([a],t.gf))
if(s.length===0)return B.x
r=t.N
return A.j(["data-arcane-action",s],r,r)},
zs(a,b,c,d,e,f,g,h,i,j,k){var s=h?"open":"closed",r=t.N,q=A.j(["data-arcane-surface",k,"data-arcane-id",g,"data-arcane-state",s],r,r)
if(!f)q.i(0,"data-arcane-focus-trap","false")
if(!j)q.i(0,"data-arcane-scrim-closes","false")
if(a!=null&&a.length!==0)q.i(0,"data-arcane-anchor",a)
if(c!=null&&c.length!==0)q.i(0,"data-arcane-anchor-placement",c)
if(b!=null&&b.length!==0)q.i(0,"data-arcane-anchor-offset",b)
if(!h)q.i(0,"hidden","")
return q},
CB(a,b,c,d,e){var s=t.N,r=A.j(["data-arcane-group",c,"data-arcane-group-mode",d],s,s)
if(e.length!==0)r.i(0,"data-arcane-group-value",e)
if(b)r.i(0,"data-arcane-group-disabled","true")
if(a!=null&&a.length!==0)r.i(0,"data-arcane-group-change",a)
return r},
CC(a,b,c,d){var s=c?"selected":"unselected",r=t.N,q=A.j(["data-arcane-group",b,"data-arcane-value",d,"data-arcane-state",s],r,r)
if(a)q.i(0,"data-arcane-disabled","true")
return q},
nf(a){var s,r,q,p,o,n="class",m=t.N,l=A.t(m,m)
for(m=a.length,s=0;s<a.length;a.length===m||(0,A.I)(a),++s)for(r=a[s].gaF(),r=r.gC(r);r.p();){q=r.gu()
p=q.a
o=p==="class"&&l.K(n)
q=q.b
if(o)l.i(0,n,A.w(l.j(0,n))+" "+q)
else l.i(0,p,q)}return l},
Dt(a,b,c,d,e,f,g){var s,r,q,p,o=$.CQ(),n=o.a,m=n.get(a)
if(m!=null)return m
s=A.AI(f)
r=A.AI(c)
q=A.Al(s,e,!0,g,":root, html.light, .light")+"\n\n"+(A.Al(r,e,!1,g,"html.dark, .dark")+"\n")
p=d+"\n\n"+(q.charCodeAt(0)==0?q:q)+"\n\n@font-face {\n  font-family: 'lucide';\n  src: url('/assets/fonts/lucide/lucide.woff2') format('woff2'),\n       url('/fonts/lucide/lucide.woff2') format('woff2'),\n       url('assets/fonts/lucide/lucide.woff2') format('woff2'),\n       url('fonts/lucide/lucide.woff2') format('woff2'),\n       url('../assets/fonts/lucide/lucide.woff2') format('woff2'),\n       url('../fonts/lucide/lucide.woff2') format('woff2'),\n       url('../../assets/fonts/lucide/lucide.woff2') format('woff2'),\n       url('../../fonts/lucide/lucide.woff2') format('woff2'),\n       url('https://cdn.jsdelivr.net/gh/ArcaneArts/arcane_jaspr@master/assets/fonts/lucide/lucide.woff2') format('woff2'),\n       url('/assets/fonts/lucide/lucide.woff') format('woff'),\n       url('/fonts/lucide/lucide.woff') format('woff'),\n       url('assets/fonts/lucide/lucide.woff') format('woff'),\n       url('fonts/lucide/lucide.woff') format('woff'),\n       url('../assets/fonts/lucide/lucide.woff') format('woff'),\n       url('../fonts/lucide/lucide.woff') format('woff'),\n       url('../../assets/fonts/lucide/lucide.woff') format('woff'),\n       url('../../fonts/lucide/lucide.woff') format('woff'),\n       url('/assets/fonts/lucide/lucide.ttf') format('truetype'),\n       url('/fonts/lucide/lucide.ttf') format('truetype'),\n       url('assets/fonts/lucide/lucide.ttf') format('truetype'),\n       url('fonts/lucide/lucide.ttf') format('truetype'),\n       url('../assets/fonts/lucide/lucide.ttf') format('truetype'),\n       url('../fonts/lucide/lucide.ttf') format('truetype'),\n       url('../../assets/fonts/lucide/lucide.ttf') format('truetype'),\n       url('../../fonts/lucide/lucide.ttf') format('truetype');\n  font-weight: normal;\n  font-style: normal;\n  font-display: block;\n}\n\n*, *::before, *::after {\n  box-sizing: border-box;\n  margin: 0;\n  padding: 0;\n}\n\nhtml, body {\n  height: 100%;\n  font-family: var(--font-sans);\n  background-color: var(--background);\n  color: var(--foreground);\n  -webkit-font-smoothing: antialiased;\n  -moz-osx-font-smoothing: grayscale;\n}\n\n* {\n  scrollbar-width: thin;\n  scrollbar-color: var(--primary) transparent;\n}\n\n*::-webkit-scrollbar {\n  width: 8px;\n  height: 8px;\n}\n\n*::-webkit-scrollbar-track {\n  background: transparent;\n}\n\n*::-webkit-scrollbar-thumb {\n  background: var(--primary);\n  border-radius: 9999px;\n  border: 2px solid transparent;\n  background-clip: padding-box;\n}\n\n*::-webkit-scrollbar-thumb:hover {\n  background: color-mix(in srgb, var(--primary) 80%, white);\n  border: 2px solid transparent;\n  background-clip: padding-box;\n}\n\n*::-webkit-scrollbar-corner {\n  background: transparent;\n}\n\nhtml.dark, html.dark body,\nhtml.light, html.light body {\n  scrollbar-width: thin;\n  scrollbar-color: var(--primary) var(--background);\n}\n\nhtml::-webkit-scrollbar,\nbody::-webkit-scrollbar {\n  width: 8px;\n  height: 8px;\n}\n\nhtml.dark::-webkit-scrollbar-track,\nhtml.dark body::-webkit-scrollbar-track,\nhtml.light::-webkit-scrollbar-track,\nhtml.light body::-webkit-scrollbar-track {\n  background: var(--background);\n}\n\nhtml.dark::-webkit-scrollbar-thumb,\nhtml.dark body::-webkit-scrollbar-thumb,\nhtml.light::-webkit-scrollbar-thumb,\nhtml.light body::-webkit-scrollbar-thumb {\n  background: var(--primary);\n  border-radius: 9999px;\n  border: 2px solid transparent;\n  background-clip: padding-box;\n}\n\nhtml.dark::-webkit-scrollbar-thumb:hover,\nhtml.dark body::-webkit-scrollbar-thumb:hover,\nhtml.light::-webkit-scrollbar-thumb:hover,\nhtml.light body::-webkit-scrollbar-thumb:hover {\n  background: color-mix(in srgb, var(--primary) 80%, white);\n  border: 2px solid transparent;\n  background-clip: padding-box;\n}\n\nhtml.dark::-webkit-scrollbar-corner,\nhtml.dark body::-webkit-scrollbar-corner,\nhtml.light::-webkit-scrollbar-corner,\nhtml.light body::-webkit-scrollbar-corner {\n  background: var(--background);\n}\n\n::selection {\n  background: var(--primary);\n  color: var(--primary-foreground);\n}\n\n::-moz-selection {\n  background: var(--primary);\n  color: var(--primary-foreground);\n}\n\n.focus-ring:focus-visible {\n  outline: none;\n  box-shadow: 0 0 0 2px var(--background), 0 0 0 4px var(--ring);\n}\n\n@keyframes arcane-spin {\n  from { transform: rotate(0deg); }\n  to { transform: rotate(360deg); }\n}\n\n@keyframes arcane-fade-in {\n  from { opacity: 0; }\n  to { opacity: 1; }\n}\n\n@keyframes arcane-fade-out {\n  from { opacity: 1; }\n  to { opacity: 0; }\n}\n\n@keyframes arcane-slide-in-up {\n  from { opacity: 0; transform: translateY(10px); }\n  to { opacity: 1; transform: translateY(0); }\n}\n\n@keyframes arcane-slide-in-down {\n  from { opacity: 0; transform: translateY(-10px); }\n  to { opacity: 1; transform: translateY(0); }\n}\n\n@keyframes arcane-slide-in-left {\n  from { opacity: 0; transform: translateX(-10px); }\n  to { opacity: 1; transform: translateX(0); }\n}\n\n@keyframes arcane-slide-in-right {\n  from { opacity: 0; transform: translateX(10px); }\n  to { opacity: 1; transform: translateX(0); }\n}\n\n@keyframes arcane-scale-in {\n  from { opacity: 0; transform: scale(0.95); }\n  to { opacity: 1; transform: scale(1); }\n}\n\n@keyframes arcane-scale-out {\n  from { opacity: 1; transform: scale(1); }\n  to { opacity: 0; transform: scale(0.95); }\n}\n\n@keyframes arcane-bounce {\n  0%, 100% { transform: translateY(0); }\n  50% { transform: translateY(-10px); }\n}\n\n@keyframes arcane-pulse {\n  0%, 100% { opacity: 1; }\n  50% { opacity: 0.5; }\n}\n\n@keyframes arcane-dropdown-fade {\n  from { opacity: 0; transform: scale(0.95) translateY(-4px); }\n  to { opacity: 1; transform: scale(1) translateY(0); }\n}\n\n.arcane-button:hover:not([disabled]) {\n  filter: brightness(0.95);\n}\n\n.arcane-button:active:not([disabled]) {\n  filter: brightness(0.9);\n}\n\n@keyframes scroll-carousel {\n  0% { transform: translateX(0); }\n  100% { transform: translateX(-50%); }\n}\n\n.arcane-carousel-track:hover {\n  animation-play-state: paused;\n}\n\n.arcane-carousel-track.dragging {\n  animation: none !important;\n  cursor: grabbing;\n}\n\n.arcane-carousel-track.dragging * {\n  pointer-events: none;\n}\n\n.arcane-carousel-track.resuming {\n  transition: none;\n}\n\n\n"+b+"\n"
o.$ti.h("1?").a(p)
n.set(a,p)
return p},
Al(a,b,c,d,e){var s=a.b,r=a.c,q=a.d,p=a.x,o=a.Q,n=a.ax,m=a.cy,l=a.db,k=a.fx,j=a.k1,i=a.ok,h=a.p4
h=e+" {\n"+("  --background: "+A.a9(s)+";\n")+("  --foreground: "+A.a9(r)+";\n")+"\n"+("  --card: "+A.a9(q)+";\n")+("  --card-foreground: "+A.a9(a.e)+";\n")+("  --card-hover: "+A.a9(a.f)+";\n")+("  --popover: "+A.a9(a.r)+";\n")+("  --popover-foreground: "+A.a9(a.w)+";\n")+"\n"+("  --primary: "+A.a9(p)+";\n")+("  --primary-foreground: "+A.a9(a.y)+";\n")+("  --primary-container: "+A.a9(a.z)+";\n")+"\n"+("  --secondary: "+A.a9(o)+";\n")+("  --secondary-foreground: "+A.a9(a.as)+";\n")+("  --secondary-container: "+A.a9(a.at)+";\n")+"\n"+("  --accent: "+A.a9(n)+";\n")+("  --accent-foreground: "+A.a9(a.ay)+";\n")+("  --accent-hover: "+A.a9(a.ch)+";\n")+("  --accent-container: "+A.a9(a.CW)+";\n")+"\n"+("  --muted: "+A.a9(a.cx)+";\n")+("  --muted-foreground: "+A.a9(m)+";\n")+"\n"+("  --destructive: "+A.a9(l)+";\n")+("  --destructive-foreground: "+A.a9(a.dx)+";\n")+("  --destructive-hover: "+A.a9(a.dy)+";\n")+("  --destructive-container: "+A.a9(a.fr)+";\n")+"\n"+("  --success: "+A.a9(k)+";\n")+("  --success-foreground: "+A.a9(a.fy)+";\n")+("  --success-hover: "+A.a9(a.go)+";\n")+("  --success-container: "+A.a9(a.id)+";\n")+"\n"+("  --warning: "+A.a9(j)+";\n")+("  --warning-foreground: "+A.a9(a.k2)+";\n")+("  --warning-hover: "+A.a9(a.k3)+";\n")+("  --warning-container: "+A.a9(a.k4)+";\n")+"\n"+("  --info: "+A.a9(i)+";\n")+("  --info-foreground: "+A.a9(a.p1)+";\n")+("  --info-hover: "+A.a9(a.p2)+";\n")+("  --info-container: "+A.a9(a.p3)+";\n")+"\n"+("  --border: "+A.a9(h)+";\n")+("  --input: "+A.a9(a.R8)+";\n")+("  --ring: "+A.a9(a.RG)+";\n")+"\n"+("  --overlay: "+A.AJ(a.rx)+";\n")+("  --navbar: "+A.AJ(a.ry)+";\n")+("  --code-background: "+A.a9(a.to)+";\n")+"\n"+("  --background-rgb: "+A.b7(s)+";\n")+("  --foreground-rgb: "+A.b7(r)+";\n")+("  --primary-rgb: "+A.b7(p)+";\n")+("  --secondary-rgb: "+A.b7(o)+";\n")+("  --accent-rgb: "+A.b7(n)+";\n")+("  --muted-rgb: "+A.b7(m)+";\n")+("  --destructive-rgb: "+A.b7(l)+";\n")+("  --success-rgb: "+A.b7(k)+";\n")+("  --warning-rgb: "+A.b7(j)+";\n")+("  --info-rgb: "+A.b7(i)+";\n")+("  --card-rgb: "+A.b7(q)+";\n")+("  --border-rgb: "+A.b7(h)+";\n")+"\n"+("  --shadow-xs: "+a.x1+";\n")+("  --shadow-sm: "+a.x2+";\n")+("  --shadow-md: "+a.xr+";\n")+("  --shadow-lg: "+a.y1+";\n")+("  --shadow-xl: "+a.y2+";\n")+"\n"
if(c){s=b.a
s=h+("  --font-sans: "+s+";\n")+("  --font-heading: "+s+";\n")+"  --font-mono: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;\n\n  --radius: 8px;\n  --radius-xs: 4px;\n  --radius-sm: 6px;\n  --radius-md: 8px;\n  --radius-lg: 10px;\n  --radius-xl: 14px;\n  --radius-2xl: 18px;\n  --radius-full: 9999px;\n\n  --space-0: 0;\n  --space-px: 1px;\n  --space-0-5: 0.125rem;\n  --space-1: 0.25rem;\n  --space-1-5: 0.375rem;\n  --space-2: 0.5rem;\n  --space-2-5: 0.625rem;\n  --space-3: 0.75rem;\n  --space-3-5: 0.875rem;\n  --space-4: 1rem;\n  --space-5: 1.25rem;\n  --space-6: 1.5rem;\n  --space-7: 1.75rem;\n  --space-8: 2rem;\n  --space-9: 2.25rem;\n  --space-10: 2.5rem;\n  --space-12: 3rem;\n  --space-14: 3.5rem;\n  --space-16: 4rem;\n  --space-20: 5rem;\n  --space-24: 6rem;\n  --space-32: 8rem;\n\n  --font-size-xs: 0.75rem;\n  --font-size-sm: 0.875rem;\n  --font-size-base: 1rem;\n  --font-size-lg: 1.125rem;\n  --font-size-xl: 1.25rem;\n  --font-size-2xl: 1.5rem;\n  --font-size-3xl: 1.875rem;\n  --font-size-4xl: 2.25rem;\n  --font-size-5xl: 3rem;\n\n  --font-weight-normal: 400;\n  --font-weight-medium: 500;\n  --font-weight-semibold: 600;\n  --font-weight-bold: 700;\n\n  --transition-fast: 100ms ease;\n  --transition: 150ms ease;\n  --transition-slow: 200ms ease;\n  --transition-slower: 300ms ease;\n\n  --arcane-background: var(--background);\n  --arcane-background-secondary: var(--muted);\n  --arcane-background-tertiary: var(--secondary);\n  --arcane-surface: var(--card);\n  --arcane-surface-variant: var(--secondary);\n  --arcane-card: var(--card);\n  --arcane-card-hover: var(--card-hover);\n  --arcane-card-alt: var(--secondary);\n  --arcane-input: var(--input);\n  --arcane-popover: var(--popover);\n  --arcane-navbar: var(--navbar);\n\n  --arcane-primary: var(--primary);\n  --arcane-secondary: var(--secondary);\n  --arcane-accent: var(--accent);\n  --arcane-muted: var(--muted);\n\n  --arcane-foreground: var(--foreground);\n  --arcane-on-background: var(--foreground);\n  --arcane-on-surface: var(--card-foreground);\n  --arcane-card-foreground: var(--card-foreground);\n  --arcane-muted-foreground: var(--muted-foreground);\n  --arcane-text-subtle: var(--muted-foreground);\n  --arcane-text-faint: color-mix(in srgb, var(--muted-foreground) 60%, transparent);\n  --arcane-primary-foreground: var(--primary-foreground);\n  --arcane-secondary-foreground: var(--secondary-foreground);\n  --arcane-accent-foreground: var(--accent-foreground);\n\n  --arcane-border: var(--border);\n  --arcane-border-subtle: var(--input);\n  --arcane-border-medium: var(--border);\n  --arcane-border-light: color-mix(in srgb, var(--border) 50%, transparent);\n  --arcane-ring: var(--ring);\n\n  --arcane-success: var(--success);\n  --arcane-success-foreground: var(--success-foreground);\n  --arcane-warning: var(--warning);\n  --arcane-warning-foreground: var(--warning-foreground);\n  --arcane-error: var(--destructive);\n  --arcane-error-foreground: var(--destructive-foreground);\n  --arcane-destructive: var(--destructive);\n  --arcane-destructive-foreground: var(--destructive-foreground);\n  --arcane-info: var(--info);\n  --arcane-info-foreground: var(--info-foreground);\n\n  --arcane-tooltip: var(--popover);\n  --arcane-tooltip-foreground: var(--popover-foreground);\n  --arcane-code-background: var(--code-background);\n\n  --arcane-background-rgb: var(--background-rgb);\n  --arcane-foreground-rgb: var(--foreground-rgb);\n  --arcane-primary-rgb: var(--primary-rgb);\n  --arcane-secondary-rgb: var(--secondary-rgb);\n  --arcane-accent-rgb: var(--accent-rgb);\n  --arcane-muted-rgb: var(--muted-rgb);\n  --arcane-destructive-rgb: var(--destructive-rgb);\n  --arcane-success-rgb: var(--success-rgb);\n  --arcane-warning-rgb: var(--warning-rgb);\n  --arcane-info-rgb: var(--info-rgb);\n  --arcane-card-rgb: var(--card-rgb);\n  --arcane-border-rgb: var(--border-rgb);\n\n  --arcane-shadow-xs: var(--shadow-xs);\n  --arcane-shadow-sm: var(--shadow-sm);\n  --arcane-shadow-md: var(--shadow-md);\n  --arcane-shadow-lg: var(--shadow-lg);\n  --arcane-shadow-xl: var(--shadow-xl);\n\n  --arcane-radius: var(--radius);\n  --arcane-radius-xs: var(--radius-xs);\n  --arcane-radius-sm: var(--radius-sm);\n  --arcane-radius-md: var(--radius-md);\n  --arcane-radius-lg: var(--radius-lg);\n  --arcane-radius-xl: var(--radius-xl);\n  --arcane-radius-2xl: var(--radius-2xl);\n  --arcane-radius-full: var(--radius-full);\n\n  --arcane-font-sans: var(--font-sans);\n  --arcane-font-heading: var(--font-heading);\n  --arcane-font-mono: var(--font-mono);\n  --arcane-font-size-xs: var(--font-size-xs);\n  --arcane-font-size-sm: var(--font-size-sm);\n  --arcane-font-size-base: var(--font-size-base);\n  --arcane-font-size-lg: var(--font-size-lg);\n  --arcane-font-size-xl: var(--font-size-xl);\n  --arcane-font-size-2xl: var(--font-size-2xl);\n  --arcane-font-size-3xl: var(--font-size-3xl);\n  --arcane-font-size-4xl: var(--font-size-4xl);\n  --arcane-font-size-5xl: var(--font-size-5xl);\n  --arcane-font-weight-normal: var(--font-weight-normal);\n  --arcane-font-weight-medium: var(--font-weight-medium);\n  --arcane-font-weight-semibold: var(--font-weight-semibold);\n  --arcane-font-weight-bold: var(--font-weight-bold);\n\n  --arcane-space-0: var(--space-0);\n  --arcane-space-px: var(--space-px);\n  --arcane-space-0-5: var(--space-0-5);\n  --arcane-space-1: var(--space-1);\n  --arcane-space-1-5: var(--space-1-5);\n  --arcane-space-2: var(--space-2);\n  --arcane-space-2-5: var(--space-2-5);\n  --arcane-space-3: var(--space-3);\n  --arcane-space-3-5: var(--space-3-5);\n  --arcane-space-4: var(--space-4);\n  --arcane-space-5: var(--space-5);\n  --arcane-space-6: var(--space-6);\n  --arcane-space-7: var(--space-7);\n  --arcane-space-8: var(--space-8);\n  --arcane-space-9: var(--space-9);\n  --arcane-space-10: var(--space-10);\n  --arcane-space-12: var(--space-12);\n  --arcane-space-14: var(--space-14);\n  --arcane-space-16: var(--space-16);\n  --arcane-space-20: var(--space-20);\n  --arcane-space-24: var(--space-24);\n  --arcane-space-32: var(--space-32);\n\n  --arcane-transition-fast: var(--transition-fast);\n  --arcane-transition: var(--transition);\n  --arcane-transition-slow: var(--transition-slow);\n  --arcane-transition-slower: var(--transition-slower);\n"}else s=h
s+="}\n"
return s.charCodeAt(0)==0?s:s},
AI(b8){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e,d,c,b,a,a0,a1,a2,a3,a4,a5,a6,a7,a8,a9,b0=null,b1=4278190080,b2=4294967295,b3=b8.y,b4=b8.a,b5=b8.b,b6=A.hE(b5)>0.179?b1:b2,b7=b8.c
if(b7==null)b7=b3?A.cM(b5,0.06):A.bB(b4,A.bY(b5,0.06),0.12)
s=b8.d
if(s==null)s=b3?b7:A.bB(b4,A.bY(b5,0.1),0.18)
r=b8.e
r=b3?A.cM(b5,0.18):A.bB(b4,A.bY(b5,0.18),0.1)
q=b3?A.cM(b5,0.04):A.bB(b4,A.bY(b5,0.02),0.05)
p=b3?A.cM(q,0.04):A.bB(b4,A.bY(q,0.04),0.06)
o=b3?b7:A.bB(b4,A.bY(b5,0.05),0.1)
n=A.bB(b6,b5,b3?0.6:0.45)
m=A.hE(b4)>0.179?b1:b2
l=b8.f
k=b8.r
j=b8.w
i=b8.x
h=A.bB(b4,b5,0.1)
g=A.bB(b7,b5,0.1)
f=b3?A.cM(s,0.05):A.bY(s,0.05)
e=A.bB(s,b5,0.1)
d=A.hE(l)>0.179?b1:b2
c=b3?A.cM(l,0.1):A.bY(l,0.1)
b=A.bB(l,b5,0.1)
a=A.hE(k)>0.179?b1:b2
a0=b3?A.cM(k,0.1):A.bY(k,0.1)
a1=A.bB(k,b5,0.1)
a2=A.hE(j)>0.179?b1:b2
a3=b3?A.cM(j,0.1):A.bY(j,0.1)
a4=A.bB(j,b5,0.1)
a5=A.hE(i)>0.179?b1:b2
a6=b3?A.cM(i,0.1):A.bY(i,0.1)
a7=A.bB(i,b5,0.1)
a8=b3?A.bY(r,0.03):r
a9=b3?A.cM(b4,0.2):b4
return new A.t7(b5,b6,q,b6,p,q,b6,b4,m,h,b7,b6,g,s,b6,f,e,o,n,l,d,c,b,k,a,a0,a1,j,a2,a3,a4,i,a5,a6,a7,r,a8,a9,2147483648,(B.c.a3(204,0,255)<<24|b5&16777215)>>>0,b7,A.Ey(b3,b0),A.Ew(b3,b0),A.Ev(b3,b0),A.Eu(b3,b0),A.Ex(b3,b0))},
hE(a){return 0.2126*A.yP(a>>>16&255)+0.7152*A.yP(a>>>8&255)+0.0722*A.yP(a&255)},
yP(a){var s=a/255
return s<=0.03928?s/12.92:Math.pow((s+0.055)/1.055,2.4)},
bY(a,b){var s=1-b
return(a&4278190080|B.c.a3(B.e.ac((a>>>16&255)*s),0,255)<<16|B.c.a3(B.e.ac((a>>>8&255)*s),0,255)<<8|B.c.a3(B.e.ac((a&255)*s),0,255))>>>0},
cM(a,b){var s=a>>>16&255,r=a>>>8&255,q=a&255
return(a&4278190080|B.c.a3(B.e.ac(s+(255-s)*b),0,255)<<16|B.c.a3(B.e.ac(r+(255-r)*b),0,255)<<8|B.c.a3(B.e.ac(q+(255-q)*b),0,255))>>>0},
bB(a,b,c){var s=1-c
return(B.c.a3(B.e.ac((a>>>16&255)*c+(b>>>16&255)*s),0,255)<<16|B.c.a3(B.e.ac((a>>>8&255)*c+(b>>>8&255)*s),0,255)<<8|B.c.a3(B.e.ac((a&255)*c+(b&255)*s),0,255)|4278190080)>>>0},
b7(a){return""+(a>>>16&255)+", "+(a>>>8&255)+", "+(a&255)},
a9(a){return"#"+B.a.dw(B.c.dH(a>>>16&255,16),2,"0")+B.a.dw(B.c.dH(a>>>8&255,16),2,"0")+B.a.dw(B.c.dH(a&255,16),2,"0")},
AJ(a){return"rgba("+(a>>>16&255)+", "+(a>>>8&255)+", "+(a&255)+", "+B.e.Z((a>>>24&255)/255,2)+")"},
Ey(a,b){if(b!=null&&a)return"0 1px 2px rgba(0, 0, 0, 0.05), 0 0 8px rgba("+A.b7(b)+", 0.08)"
return"0 1px 2px rgba(0, 0, 0, 0.05)"},
Ew(a,b){if(b!=null&&a)return"0 1px 3px rgba(0, 0, 0, 0.1), 0 0 12px rgba("+A.b7(b)+", 0.1)"
return"0 1px 3px rgba(0, 0, 0, 0.1), 0 1px 2px rgba(0, 0, 0, 0.06)"},
Ev(a,b){if(b!=null&&a)return"0 4px 6px rgba(0, 0, 0, 0.1), 0 0 20px rgba("+A.b7(b)+", 0.15)"
return"0 4px 6px rgba(0, 0, 0, 0.1), 0 2px 4px rgba(0, 0, 0, 0.06)"},
Eu(a,b){if(b!=null&&a)return"0 10px 15px rgba(0, 0, 0, 0.1), 0 0 30px rgba("+A.b7(b)+", 0.2)"
return"0 10px 15px rgba(0, 0, 0, 0.1), 0 4px 6px rgba(0, 0, 0, 0.05)"},
Ex(a,b){if(b!=null&&a)return"0 20px 25px rgba(0, 0, 0, 0.15), 0 0 50px rgba("+A.b7(b)+", 0.25)"
return"0 20px 25px rgba(0, 0, 0, 0.15), 0 8px 10px rgba(0, 0, 0, 0.04)"},
ES(a){var s,r,q,p,o,n=null
A:{s=!1
r=!1
q=n
p=r
q=!1===p
p=q
if(p){p="minmax(0, auto) minmax(0, 1fr)"
break A}o=!1
p=!1
if(p)p=s
else p=!1
if(p){p="minmax(0, 1fr) minmax(0, auto)"
break A}if(o)p=q
else p=!1
if(p){p="minmax(0, 1fr)"
break A}p=n}return p},
Hs(a,b,c,d){var s,r,q,p,o,n=A.t(d,c.h("q<0>"))
for(s=c.h("D<0>"),r=0;r<1;++r){q=a[r]
p=b.$1(q)
o=n.j(0,p)
if(o==null){o=A.a([],s)
n.i(0,p,o)
p=o}else p=o
J.fJ(p,q)}return n},
bt(a){var s,r=a.c.a.j(0,"charset")
if(a.a==="application"&&a.b==="json"&&r==null)return B.l
if(r!=null){s=A.Aq(r)
if(s==null)s=B.q}else s=B.q
return s},
CO(a){return a},
Il(a){return new A.ey(a)},
In(a,b,c,d){var s,r,q,p
try{q=c.$0()
return q}catch(p){q=A.a1(p)
if(q instanceof A.fa){s=q
throw A.d(A.EV("Invalid "+a+": "+s.a,s.b,s.gcH()))}else if(t.lW.b(q)){r=q
throw A.d(A.ap("Invalid "+a+' "'+b+'": '+r.geZ(),r.gcH(),r.ga6()))}else throw p}},
qB(a){return new A.d4(A.Et(a),t.kP)},
Et(a){return function(){var s=a
var r=0,q=1,p=[],o,n
return function $async$qB(b,c,d){if(c===1){p.push(d)
r=q}for(;;)switch(r){case 0:o=0
case 2:if(!(o<A.bb(s.length))){r=4
break}n=A.a7(s.item(o))
n.toString
r=5
return b.b=n,1
case 5:case 3:++o
r=2
break
case 4:return 0
case 1:return b.c=p.at(-1),3}}}},
n3(a,b,c,d){return A.t(t.N,t.v)},
Cu(){var s,r,q,p,o=null
try{o=A.yY()}catch(s){if(t.mA.b(A.a1(s))){r=$.xg
if(r!=null)return r
throw s}else throw s}if(J.a8(o,$.BY)){r=$.xg
r.toString
return r}$.BY=o
if($.zu()===$.iZ())r=$.xg=o.i6(".").k(0)
else{q=o.fc()
p=q.length-1
r=$.xg=p===0?q:B.a.q(q,0,p)}return r},
CF(a){var s
if(!(a>=65&&a<=90))s=a>=97&&a<=122
else s=!0
return s},
Cv(a,b){var s,r,q=null,p=a.length,o=b+2
if(p<o)return q
if(!(b>=0&&b<p))return A.f(a,b)
if(!A.CF(a.charCodeAt(b)))return q
s=b+1
if(!(s<p))return A.f(a,s)
if(a.charCodeAt(s)!==58){r=b+4
if(p<r)return q
if(B.a.q(a,s,r).toLowerCase()!=="%3a")return q
b=o}s=b+2
if(p===s)return s
if(!(s>=0&&s<p))return A.f(a,s)
if(a.charCodeAt(s)!==47)return q
return b+3},
EH(a,b){if(b==null)return!0
return b===a},
I0(a){var s,r,q,p
if(a.gn(0)===0)return!0
s=a.gaz(0)
for(r=A.e4(a,1,null,a.$ti.h("z.E")),q=r.$ti,r=new A.aw(r,r.gn(0),q.h("aw<z.E>")),q=q.h("z.E");r.p();){p=r.d
if(!J.a8(p==null?q.a(p):p,s))return!1}return!0},
Ie(a,b,c){var s=B.b.aU(a,null)
if(s<0)throw A.d(A.ai(A.w(a)+" contains no null elements.",null))
B.b.i(a,s,b)},
CL(a,b,c){var s=B.b.aU(a,b)
if(s<0)throw A.d(A.ai(A.w(a)+" contains no elements matching "+b.k(0)+".",null))
B.b.i(a,s,null)},
Hg(a,b){var s,r,q,p
for(s=new A.c9(a),r=t.gS,s=new A.aw(s,s.gn(0),r.h("aw<T.E>")),r=r.h("T.E"),q=0;s.p();){p=s.d
if((p==null?r.a(p):p)===b)++q}return q},
xZ(a,b,c){var s,r,q
if(b.length===0)for(s=0;;){r=B.a.aV(a,"\n",s)
if(r===-1)return a.length-s>=c?s:null
if(r-s>=c)return s
s=r+1}r=B.a.aU(a,b)
while(r!==-1){q=r===0?0:B.a.dt(a,"\n",r-1)+1
if(c===r-q)return q
r=B.a.aV(a,b,r+1)}return null}},B={}
var w=[A,J,B]
var $={}
A.yL.prototype={}
J.k6.prototype={
N(a,b){return a===b},
gI(a){return A.b0(a)},
k(a){return"Instance of '"+A.kF(a)+"'"},
ga2(a){return A.bc(A.za(this))}}
J.hm.prototype={
k(a){return String(a)},
gI(a){return a?519018:218159},
ga2(a){return A.bc(t.k4)},
$iag:1,
$iy:1}
J.ho.prototype={
N(a,b){return null==b},
k(a){return"null"},
gI(a){return 0},
$iag:1,
$iaa:1}
J.hq.prototype={$ia4:1}
J.dl.prototype={
gI(a){return 0},
ga2(a){return B.k6},
k(a){return String(a)}}
J.kC.prototype={}
J.e7.prototype={}
J.dk.prototype={
k(a){var s=a[$.yk()]
if(s==null)return this.iU(a)
return"JavaScript function for "+J.aF(s)},
$icB:1}
J.hp.prototype={
gI(a){return 0},
k(a){return String(a)}}
J.hr.prototype={
gI(a){return 0},
k(a){return String(a)}}
J.D.prototype={
cc(a,b){return new A.cw(a,A.F(a).h("@<1>").A(b).h("cw<1,2>"))},
m(a,b){A.F(a).c.a(b)
a.$flags&1&&A.au(a,29)
a.push(b)},
bR(a,b){a.$flags&1&&A.au(a,"removeAt",1)
if(b<0||b>=a.length)throw A.d(A.qL(b,null))
return a.splice(b,1)[0]},
cg(a,b,c){A.F(a).c.a(c)
a.$flags&1&&A.au(a,"insert",2)
if(b<0||b>a.length)throw A.d(A.qL(b,null))
a.splice(b,0,c)},
eV(a,b,c){var s,r
A.F(a).h("m<1>").a(c)
a.$flags&1&&A.au(a,"insertAll",2)
A.yR(b,0,a.length,"index")
if(!t.gt.b(c))c=J.Dp(c)
s=J.b4(c)
a.length=a.length+s
r=b+s
this.bg(a,r,a.length,a,b)
this.cC(a,b,r,c)},
i_(a){a.$flags&1&&A.au(a,"removeLast",1)
if(a.length===0)throw A.d(A.n1(a,-1))
return a.pop()},
J(a,b){var s
a.$flags&1&&A.au(a,"remove",1)
for(s=0;s<a.length;++s)if(J.a8(a[s],b)){a.splice(s,1)
return!0}return!1},
i1(a,b){A.F(a).h("y(1)").a(b)
a.$flags&1&&A.au(a,16)
this.hh(a,b,!0)},
hh(a,b,c){var s,r,q,p,o
A.F(a).h("y(1)").a(b)
s=[]
r=a.length
for(q=0;q<r;++q){p=a[q]
if(!b.$1(p))s.push(p)
if(a.length!==r)throw A.d(A.aB(a))}o=s.length
if(o===r)return
this.sn(a,o)
for(q=0;q<s.length;++q)a[q]=s[q]},
dJ(a,b){var s=A.F(a)
return new A.a3(a,s.h("y(1)").a(b),s.h("a3<1>"))},
B(a,b){var s
A.F(a).h("m<1>").a(b)
a.$flags&1&&A.au(a,"addAll",2)
if(Array.isArray(b)){this.j9(a,b)
return}for(s=J.aE(b);s.p();)a.push(s.gu())},
j9(a,b){var s,r
t.dG.a(b)
s=b.length
if(s===0)return
if(a===b)throw A.d(A.aB(a))
for(r=0;r<s;++r)a.push(b[r])},
O(a){a.$flags&1&&A.au(a,"clear","clear")
a.length=0},
aZ(a,b,c){var s=A.F(a)
return new A.E(a,s.A(c).h("1(2)").a(b),s.h("@<1>").A(c).h("E<1,2>"))},
aA(a,b){var s,r=A.bL(a.length,"",!1,t.N)
for(s=0;s<a.length;++s)this.i(r,s,A.w(a[s]))
return r.join(b)},
aC(a,b){return A.e4(a,b,null,A.F(a).c)},
eQ(a,b,c,d){var s,r,q
d.a(b)
A.F(a).A(d).h("1(1,2)").a(c)
s=a.length
for(r=b,q=0;q<s;++q){r=c.$2(r,a[q])
if(a.length!==s)throw A.d(A.aB(a))}return r},
hJ(a,b){var s,r,q
A.F(a).h("y(1)").a(b)
s=a.length
for(r=0;r<s;++r){q=a[r]
if(b.$1(q))return q
if(a.length!==s)throw A.d(A.aB(a))}throw A.d(A.hl())},
X(a,b){if(!(b>=0&&b<a.length))return A.f(a,b)
return a[b]},
b4(a,b,c){var s=a.length
if(b>s)throw A.d(A.an(b,0,s,"start",null))
if(c<b||c>s)throw A.d(A.an(c,b,s,"end",null))
if(b===c)return A.a([],A.F(a))
return A.a(a.slice(b,c),A.F(a))},
gaz(a){if(a.length>0)return a[0]
throw A.d(A.hl())},
gaL(a){var s=a.length
if(s>0)return a[s-1]
throw A.d(A.hl())},
bg(a,b,c,d,e){var s,r,q,p,o
A.F(a).h("m<1>").a(d)
a.$flags&2&&A.au(a,5)
A.ch(b,c,a.length)
s=c-b
if(s===0)return
A.bo(e,"skipCount")
if(t._.b(d)){r=d
q=e}else{r=J.ns(d,e).b0(0,!1)
q=0}p=J.aT(r)
if(q+s>p.gn(r))throw A.d(A.At())
if(q<b)for(o=s-1;o>=0;--o)a[b+o]=p.j(r,q+o)
else for(o=0;o<s;++o)a[b+o]=p.j(r,q+o)},
cC(a,b,c,d){return this.bg(a,b,c,d,0)},
bG(a,b){var s,r
A.F(a).h("y(1)").a(b)
s=a.length
for(r=0;r<s;++r){if(b.$1(a[r]))return!0
if(a.length!==s)throw A.d(A.aB(a))}return!1},
ai(a,b){var s,r,q,p,o,n=A.F(a)
n.h("h(1,1)?").a(b)
a.$flags&2&&A.au(a,"sort")
s=a.length
if(s<2)return
if(b==null)b=J.Gr()
if(s===2){r=a[0]
q=a[1]
n=b.$2(r,q)
if(typeof n!=="number")return n.al()
if(n>0){a[0]=q
a[1]=r}return}p=0
if(n.c.b(null))for(o=0;o<a.length;++o)if(a[o]===void 0){a[o]=null;++p}a.sort(A.fG(b,2))
if(p>0)this.kY(a,p)},
fl(a){return this.ai(a,null)},
kY(a,b){var s,r=a.length
for(;s=r-1,r>0;r=s)if(a[s]===null){a[s]=void 0;--b
if(b===0)break}},
aU(a,b){var s,r=a.length
if(0>=r)return-1
for(s=0;s<r;++s){if(!(s<a.length))return A.f(a,s)
if(J.a8(a[s],b))return s}return-1},
v(a,b){var s
for(s=0;s<a.length;++s)if(J.a8(a[s],b))return!0
return!1},
gL(a){return a.length===0},
ga1(a){return a.length!==0},
k(a){return A.yI(a,"[","]")},
b0(a,b){var s=A.a(a.slice(0),A.F(a))
return s},
dG(a){return this.b0(a,!0)},
gC(a){return new J.dI(a,a.length,A.F(a).h("dI<1>"))},
gI(a){return A.b0(a)},
gn(a){return a.length},
sn(a,b){a.$flags&1&&A.au(a,"set length","change the length of")
if(b<0)throw A.d(A.an(b,0,null,"newLength",null))
if(b>a.length)A.F(a).c.a(null)
a.length=b},
j(a,b){if(!(b>=0&&b<a.length))throw A.d(A.n1(a,b))
return a[b]},
i(a,b,c){A.F(a).c.a(c)
a.$flags&2&&A.au(a)
if(!(b>=0&&b<a.length))throw A.d(A.n1(a,b))
a[b]=c},
bM(a,b){var s
A.F(a).h("y(1)").a(b)
if(0>=a.length)return-1
for(s=0;s<a.length;++s)if(b.$1(a[s]))return s
return-1},
ga2(a){return A.bc(A.F(a))},
$iK:1,
$im:1,
$iq:1}
J.k8.prototype={
nj(a){var s,r,q
if(!Array.isArray(a))return null
s=a.$flags|0
if((s&4)!==0)r="const, "
else if((s&2)!==0)r="unmodifiable, "
else r=(s&1)!==0?"fixed, ":""
q="Instance of '"+A.kF(a)+"'"
if(r==="")return q
return q+" ("+r+"length: "+a.length+")"}}
J.q_.prototype={}
J.dI.prototype={
gu(){var s=this.d
return s==null?this.$ti.c.a(s):s},
p(){var s,r=this,q=r.a,p=q.length
if(r.b!==p){q=A.I(q)
throw A.d(q)}s=r.c
if(s>=p){r.d=null
return!1}r.d=q[s]
r.c=s+1
return!0},
$iab:1}
J.eQ.prototype={
P(a,b){var s
A.at(b)
if(a<b)return-1
else if(a>b)return 1
else if(a===b){if(a===0){s=this.gds(b)
if(this.gds(a)===s)return 0
if(this.gds(a))return-1
return 1}return 0}else if(isNaN(a)){if(isNaN(b))return 0
return 1}else return-1},
gds(a){return a===0?1/a<0:a<0},
bv(a){var s
if(a>=-2147483648&&a<=2147483647)return a|0
if(isFinite(a)){s=a<0?Math.ceil(a):Math.floor(a)
return s+0}throw A.d(A.ao(""+a+".toInt()"))},
ac(a){if(a>0){if(a!==1/0)return Math.round(a)}else if(a>-1/0)return 0-Math.round(0-a)
throw A.d(A.ao(""+a+".round()"))},
nb(a){if(a<0)return-Math.round(-a)
else return Math.round(a)},
a3(a,b,c){if(B.c.P(b,c)>0)throw A.d(A.el(b))
if(this.P(a,b)<0)return b
if(this.P(a,c)>0)return c
return a},
Z(a,b){var s
if(b>20)throw A.d(A.an(b,0,20,"fractionDigits",null))
s=a.toFixed(b)
if(a===0&&this.gds(a))return"-"+s
return s},
dH(a,b){var s,r,q,p,o
if(b<2||b>36)throw A.d(A.an(b,2,36,"radix",null))
s=a.toString(b)
r=s.length
q=r-1
if(!(q>=0))return A.f(s,q)
if(s.charCodeAt(q)!==41)return s
p=/^([\da-z]+)(?:\.([\da-z]+))?\(e\+(\d+)\)$/.exec(s)
if(p==null)A.ak(A.ao("Unexpected toString result: "+s))
r=p.length
if(1>=r)return A.f(p,1)
s=p[1]
if(3>=r)return A.f(p,3)
o=+p[3]
r=p[2]
if(r!=null){s+=r
o-=r.length}return s+B.a.aB("0",o)},
k(a){if(a===0&&1/a<0)return"-0.0"
else return""+a},
gI(a){var s,r,q,p,o=a|0
if(a===o)return o&536870911
s=Math.abs(a)
r=Math.log(s)/0.6931471805599453|0
q=Math.pow(2,r)
p=s<1?s/q:q/s
return((p*9007199254740992|0)+(p*3542243181176521|0))*599197+r*1259&536870911},
bX(a,b){var s=a%b
if(s===0)return 0
if(s>0)return s
return s+b},
ag(a,b){return(a|0)===a?a/b|0:this.ls(a,b)},
ls(a,b){var s=a/b
if(s>=-2147483648&&s<=2147483647)return s|0
if(s>0){if(s!==1/0)return Math.floor(s)}else if(s>-1/0)return Math.ceil(s)
throw A.d(A.ao("Result of truncating division is "+A.w(s)+": "+A.w(a)+" ~/ "+b))},
iD(a,b){if(b<0)throw A.d(A.el(b))
return b>31?0:a<<b>>>0},
bm(a,b){var s
if(a>0)s=this.hj(a,b)
else{s=b>31?31:b
s=a>>s>>>0}return s},
lj(a,b){if(0>b)throw A.d(A.el(b))
return this.hj(a,b)},
hj(a,b){return b>31?0:a>>>b},
ga2(a){return A.bc(t.cZ)},
$iax:1,
$iv:1,
$ibd:1}
J.hn.prototype={
ga2(a){return A.bc(t.S)},
$iag:1,
$ih:1}
J.k9.prototype={
ga2(a){return A.bc(t.r)},
$iag:1}
J.dj.prototype={
d8(a,b,c){var s=b.length
if(c>s)throw A.d(A.an(c,0,s,null,null))
return new A.mH(b,a,c)},
bF(a,b){return this.d8(a,b,0)},
bt(a,b,c){var s,r,q,p,o=null
if(c<0||c>b.length)throw A.d(A.an(c,0,b.length,o,o))
s=a.length
r=b.length
if(c+s>r)return o
for(q=0;q<s;++q){p=c+q
if(!(p>=0&&p<r))return A.f(b,p)
if(b.charCodeAt(p)!==a.charCodeAt(q))return o}return new A.hV(c,a)},
a8(a,b){var s=b.length,r=a.length
if(s>r)return!1
return b===this.S(a,r-s)},
i4(a,b,c,d){A.yR(d,0,a.length,"startIndex")
return A.Ij(a,b,c,d)},
dD(a,b,c){return this.i4(a,b,c,0)},
bf(a,b,c,d){var s=A.ch(b,c,a.length)
return A.CN(a,b,s,d)},
V(a,b,c){var s
if(c<0||c>a.length)throw A.d(A.an(c,0,a.length,null,null))
s=c+b.length
if(s>a.length)return!1
return b===a.substring(c,s)},
M(a,b){return this.V(a,b,0)},
q(a,b,c){return a.substring(b,A.ch(b,c,a.length))},
S(a,b){return this.q(a,b,null)},
aG(a){var s,r,q,p=a.trim(),o=p.length
if(o===0)return p
if(0>=o)return A.f(p,0)
if(p.charCodeAt(0)===133){s=J.Ej(p,1)
if(s===o)return""}else s=0
r=o-1
if(!(r>=0))return A.f(p,r)
q=p.charCodeAt(r)===133?J.Ek(p,r):o
if(s===0&&q===o)return p
return p.substring(s,q)},
aB(a,b){var s,r
if(0>=b)return""
if(b===1||a.length===0)return a
if(b!==b>>>0)throw A.d(B.cn)
for(s=a,r="";;){if((b&1)===1)r=s+r
b=b>>>1
if(b===0)break
s+=s}return r},
dw(a,b,c){var s=b-a.length
if(s<=0)return a
return this.aB(c,s)+a},
mW(a,b){var s=b-a.length
if(s<=0)return a
return a+this.aB(" ",s)},
aV(a,b,c){var s
if(c<0||c>a.length)throw A.d(A.an(c,0,a.length,null,null))
s=a.indexOf(b,c)
return s},
aU(a,b){return this.aV(a,b,0)},
dt(a,b,c){var s,r
if(c==null)c=a.length
else if(c<0||c>a.length)throw A.d(A.an(c,0,a.length,null,null))
s=b.length
r=a.length
if(c+s>r)c=r-s
return a.lastIndexOf(b,c)},
eX(a,b){return this.dt(a,b,null)},
v(a,b){return A.If(a,b,0)},
P(a,b){var s
A.r(b)
if(a===b)s=0
else s=a<b?-1:1
return s},
k(a){return a},
gI(a){var s,r,q
for(s=a.length,r=0,q=0;q<s;++q){r=r+a.charCodeAt(q)&536870911
r=r+((r&524287)<<10)&536870911
r^=r>>6}r=r+((r&67108863)<<3)&536870911
r^=r>>11
return r+((r&16383)<<15)&536870911},
ga2(a){return A.bc(t.N)},
gn(a){return a.length},
$iag:1,
$iax:1,
$iqI:1,
$ib:1}
A.du.prototype={
gC(a){return new A.fY(J.aE(this.gaQ()),A.n(this).h("fY<1,2>"))},
gn(a){return J.b4(this.gaQ())},
gL(a){return J.eq(this.gaQ())},
ga1(a){return J.zC(this.gaQ())},
aC(a,b){var s=A.n(this)
return A.Ah(J.ns(this.gaQ(),b),s.c,s.y[1])},
X(a,b){return A.n(this).y[1].a(J.nr(this.gaQ(),b))},
v(a,b){return J.zB(this.gaQ(),b)},
k(a){return J.aF(this.gaQ())}}
A.fY.prototype={
p(){return this.a.p()},
gu(){return this.$ti.y[1].a(this.a.gu())},
$iab:1}
A.dK.prototype={
gaQ(){return this.a}}
A.ia.prototype={$iK:1}
A.i6.prototype={
j(a,b){return this.$ti.y[1].a(J.be(this.a,b))},
i(a,b,c){var s=this.$ti
J.d9(this.a,b,s.c.a(s.y[1].a(c)))},
sn(a,b){J.Do(this.a,b)},
m(a,b){var s=this.$ti
J.fJ(this.a,s.c.a(s.y[1].a(b)))},
ai(a,b){var s
this.$ti.h("h(2,2)?").a(b)
s=b==null?null:new A.ub(this,b)
J.zD(this.a,s)},
$iK:1,
$iq:1}
A.ub.prototype={
$2(a,b){var s=this.a.$ti,r=s.c
r.a(a)
r.a(b)
s=s.y[1]
return this.b.$2(s.a(a),s.a(b))},
$S(){return this.a.$ti.h("h(1,1)")}}
A.cw.prototype={
cc(a,b){return new A.cw(this.a,this.$ti.h("@<1>").A(b).h("cw<1,2>"))},
gaQ(){return this.a}}
A.cG.prototype={
k(a){return"LateInitializationError: "+this.a}}
A.c9.prototype={
gn(a){return this.a.length},
j(a,b){var s=this.a
if(!(b>=0&&b<s.length))return A.f(s,b)
return s.charCodeAt(b)}}
A.y9.prototype={
$0(){return A.pq(null,t.H)},
$S:31}
A.rC.prototype={}
A.K.prototype={}
A.z.prototype={
gC(a){var s=this
return new A.aw(s,s.gn(s),A.n(s).h("aw<z.E>"))},
gL(a){return this.gn(this)===0},
gaz(a){if(this.gn(this)===0)throw A.d(A.hl())
return this.X(0,0)},
v(a,b){var s,r=this,q=r.gn(r)
for(s=0;s<q;++s){if(J.a8(r.X(0,s),b))return!0
if(q!==r.gn(r))throw A.d(A.aB(r))}return!1},
aA(a,b){var s,r,q,p=this,o=p.gn(p)
if(b.length!==0){if(o===0)return""
s=A.w(p.X(0,0))
if(o!==p.gn(p))throw A.d(A.aB(p))
for(r=s,q=1;q<o;++q){r=r+b+A.w(p.X(0,q))
if(o!==p.gn(p))throw A.d(A.aB(p))}return r.charCodeAt(0)==0?r:r}else{for(q=0,r="";q<o;++q){r+=A.w(p.X(0,q))
if(o!==p.gn(p))throw A.d(A.aB(p))}return r.charCodeAt(0)==0?r:r}},
aZ(a,b,c){var s=A.n(this)
return new A.E(this,s.A(c).h("1(z.E)").a(b),s.h("@<z.E>").A(c).h("E<1,2>"))},
n3(a,b){var s,r,q,p=this
A.n(p).h("z.E(z.E,z.E)").a(b)
s=p.gn(p)
if(s===0)throw A.d(A.hl())
r=p.X(0,0)
for(q=1;q<s;++q){r=b.$2(r,p.X(0,q))
if(s!==p.gn(p))throw A.d(A.aB(p))}return r},
eQ(a,b,c,d){var s,r,q,p=this
d.a(b)
A.n(p).A(d).h("1(1,z.E)").a(c)
s=p.gn(p)
for(r=b,q=0;q<s;++q){r=c.$2(r,p.X(0,q))
if(s!==p.gn(p))throw A.d(A.aB(p))}return r},
aC(a,b){return A.e4(this,b,null,A.n(this).h("z.E"))},
b0(a,b){var s=A.x(this,A.n(this).h("z.E"))
return s},
dG(a){return this.b0(0,!0)},
fd(a){var s,r=this,q=A.yN(A.n(r).h("z.E"))
for(s=0;s<r.gn(r);++s)q.m(0,r.X(0,s))
return q}}
A.e3.prototype={
j5(a,b,c,d){var s,r=this.b
A.bo(r,"start")
s=this.c
if(s!=null){A.bo(s,"end")
if(r>s)throw A.d(A.an(r,0,s,"start",null))}},
gjR(){var s=J.b4(this.a),r=this.c
if(r==null||r>s)return s
return r},
gln(){var s=J.b4(this.a),r=this.b
if(r>s)return s
return r},
gn(a){var s,r=J.b4(this.a),q=this.b
if(q>=r)return 0
s=this.c
if(s==null||s>=r)return r-q
return s-q},
X(a,b){var s=this,r=s.gln()+b
if(b<0||r>=s.gjR())throw A.d(A.pW(b,s.gn(0),s,"index"))
return J.nr(s.a,r)},
aC(a,b){var s,r,q=this
A.bo(b,"count")
s=q.b+b
r=q.c
if(r!=null&&s>=r)return new A.dM(q.$ti.h("dM<1>"))
return A.e4(q.a,s,r,q.$ti.c)},
b0(a,b){var s,r,q,p=this,o=p.b,n=p.a,m=J.aT(n),l=m.gn(n),k=p.c
if(k!=null&&k<l)l=k
s=l-o
if(s<=0){n=J.yJ(0,p.$ti.c)
return n}r=A.bL(s,m.X(n,o),!1,p.$ti.c)
for(q=1;q<s;++q){B.b.i(r,q,m.X(n,o+q))
if(m.gn(n)<l)throw A.d(A.aB(p))}return r}}
A.aw.prototype={
gu(){var s=this.d
return s==null?this.$ti.c.a(s):s},
p(){var s,r=this,q=r.a,p=J.aT(q),o=p.gn(q)
if(r.b!==o)throw A.d(A.aB(q))
s=r.c
if(s>=o){r.d=null
return!1}r.d=p.X(q,s);++r.c
return!0},
$iab:1}
A.bi.prototype={
gC(a){return new A.hw(J.aE(this.a),this.b,A.n(this).h("hw<1,2>"))},
gn(a){return J.b4(this.a)},
gL(a){return J.eq(this.a)},
X(a,b){return this.b.$1(J.nr(this.a,b))}}
A.dL.prototype={$iK:1}
A.hw.prototype={
p(){var s=this,r=s.b
if(r.p()){s.a=s.c.$1(r.gu())
return!0}s.a=null
return!1},
gu(){var s=this.a
return s==null?this.$ti.y[1].a(s):s},
$iab:1}
A.E.prototype={
gn(a){return J.b4(this.a)},
X(a,b){return this.b.$1(J.nr(this.a,b))}}
A.a3.prototype={
gC(a){return new A.e9(J.aE(this.a),this.b,this.$ti.h("e9<1>"))},
aZ(a,b,c){var s=this.$ti
return new A.bi(this,s.A(c).h("1(2)").a(b),s.h("@<1>").A(c).h("bi<1,2>"))}}
A.e9.prototype={
p(){var s,r
for(s=this.a,r=this.b;s.p();)if(r.$1(s.gu()))return!0
return!1},
gu(){return this.a.gu()},
$iab:1}
A.ha.prototype={
gC(a){return new A.hb(J.aE(this.a),this.b,B.aF,this.$ti.h("hb<1,2>"))}}
A.hb.prototype={
gu(){var s=this.d
return s==null?this.$ti.y[1].a(s):s},
p(){var s,r,q=this,p=q.c
if(p==null)return!1
for(s=q.a,r=q.b;!p.p();){q.d=null
if(s.p()){q.c=null
p=J.aE(r.$1(s.gu()))
q.c=p}else return!1}q.d=q.c.gu()
return!0},
$iab:1}
A.cS.prototype={
aC(a,b){A.nS(b,"count",t.S)
A.bo(b,"count")
return new A.cS(this.a,this.b+b,A.n(this).h("cS<1>"))},
gC(a){var s=this.a
return new A.hR(s.gC(s),this.b,A.n(this).h("hR<1>"))}}
A.eC.prototype={
gn(a){var s=this.a,r=s.gn(s)-this.b
if(r>=0)return r
return 0},
aC(a,b){A.nS(b,"count",t.S)
A.bo(b,"count")
return new A.eC(this.a,this.b+b,this.$ti)},
$iK:1}
A.hR.prototype={
p(){var s,r
for(s=this.a,r=0;r<this.b;++r)s.p()
this.b=0
return s.p()},
gu(){return this.a.gu()},
$iab:1}
A.dM.prototype={
gC(a){return B.aF},
gL(a){return!0},
gn(a){return 0},
X(a,b){throw A.d(A.an(b,0,0,"index",null))},
v(a,b){return!1},
aZ(a,b,c){this.$ti.A(c).h("1(2)").a(b)
return new A.dM(c.h("dM<0>"))},
aC(a,b){A.bo(b,"count")
return this},
b0(a,b){var s=J.yJ(0,this.$ti.c)
return s}}
A.h8.prototype={
p(){return!1},
gu(){throw A.d(A.hl())},
$iab:1}
A.hZ.prototype={
gC(a){return new A.i_(J.aE(this.a),this.$ti.h("i_<1>"))}}
A.i_.prototype={
p(){var s,r
for(s=this.a,r=this.$ti.c;s.p();)if(r.b(s.gu()))return!0
return!1},
gu(){return this.$ti.c.a(this.a.gu())},
$iab:1}
A.av.prototype={
sn(a,b){throw A.d(A.ao("Cannot change the length of a fixed-length list"))},
m(a,b){A.aX(a).h("av.E").a(b)
throw A.d(A.ao("Cannot add to a fixed-length list"))}}
A.co.prototype={
i(a,b,c){A.n(this).h("co.E").a(c)
throw A.d(A.ao("Cannot modify an unmodifiable list"))},
sn(a,b){throw A.d(A.ao("Cannot change the length of an unmodifiable list"))},
m(a,b){A.n(this).h("co.E").a(b)
throw A.d(A.ao("Cannot add to an unmodifiable list"))},
ai(a,b){A.n(this).h("h(co.E,co.E)?").a(b)
throw A.d(A.ao("Cannot modify an unmodifiable list"))}}
A.fh.prototype={}
A.cN.prototype={
gn(a){return J.b4(this.a)},
X(a,b){var s=this.a,r=J.aT(s)
return r.X(s,r.gn(s)-1-b)}}
A.iQ.prototype={}
A.A.prototype={$r:"+(1,2)",$s:1}
A.bQ.prototype={$r:"+(1,2,3)",$s:2}
A.ei.prototype={$r:"+id,name,snapshot(1,2,3)",$s:3}
A.b2.prototype={$r:"+(1,2,3,4)",$s:4}
A.ej.prototype={$r:"+(1,2,3,4,5)",$s:5}
A.iu.prototype={$r:"+display,history,id,name,suffix,value(1,2,3,4,5,6)",$s:6}
A.h3.prototype={}
A.h2.prototype={
gL(a){return this.gn(this)===0},
ga1(a){return this.gn(this)!==0},
k(a){return A.qq(this)},
i(a,b,c){var s=A.n(this)
s.c.a(b)
s.y[1].a(c)
A.DF()},
gaF(){return new A.d4(this.mj(),A.n(this).h("d4<W<1,2>>"))},
mj(){var s=this
return function(){var r=0,q=1,p=[],o,n,m,l,k
return function $async$gaF(a,b,c){if(b===1){p.push(c)
r=q}for(;;)switch(r){case 0:o=s.ga9(),o=o.gC(o),n=A.n(s),m=n.y[1],n=n.h("W<1,2>")
case 2:if(!o.p()){r=3
break}l=o.gu()
k=s.j(0,l)
r=4
return a.b=new A.W(l,k==null?m.a(k):k,n),1
case 4:r=2
break
case 3:return 0
case 1:return a.c=p.at(-1),3}}}},
bs(a,b,c,d){var s=A.t(c,d)
this.aa(0,new A.ou(this,A.n(this).A(c).A(d).h("W<1,2>(3,4)").a(b),s))
return s},
$iL:1}
A.ou.prototype={
$2(a,b){var s=A.n(this.a),r=this.b.$2(s.c.a(a),s.y[1].a(b))
this.c.i(0,r.a,r.b)},
$S(){return A.n(this.a).h("~(1,2)")}}
A.i.prototype={
gn(a){return this.b.length},
gfT(){var s=this.$keys
if(s==null){s=Object.keys(this.a)
this.$keys=s}return s},
K(a){if(typeof a!="string")return!1
if("__proto__"===a)return!1
return this.a.hasOwnProperty(a)},
j(a,b){if(!this.K(b))return null
return this.b[this.a[b]]},
aa(a,b){var s,r,q,p
this.$ti.h("~(1,2)").a(b)
s=this.gfT()
r=this.b
for(q=s.length,p=0;p<q;++p)b.$2(s[p],r[p])},
ga9(){return new A.ij(this.gfT(),this.$ti.h("ij<1>"))}}
A.ij.prototype={
gn(a){return this.a.length},
gL(a){return 0===this.a.length},
ga1(a){return 0!==this.a.length},
gC(a){var s=this.a
return new A.ee(s,s.length,this.$ti.h("ee<1>"))}}
A.ee.prototype={
gu(){var s=this.d
return s==null?this.$ti.c.a(s):s},
p(){var s=this,r=s.c
if(r>=s.b){s.d=null
return!1}s.d=s.a[r]
s.c=r+1
return!0},
$iab:1}
A.h4.prototype={
m(a,b){A.n(this).c.a(b)
A.DG()}}
A.h5.prototype={
gn(a){return this.b},
gL(a){return this.b===0},
ga1(a){return this.b!==0},
gC(a){var s,r=this,q=r.$keys
if(q==null){q=Object.keys(r.a)
r.$keys=q}s=q
return new A.ee(s,s.length,r.$ti.h("ee<1>"))},
v(a,b){if("__proto__"===b)return!1
return this.a.hasOwnProperty(b)}}
A.k4.prototype={
N(a,b){if(b==null)return!1
return b instanceof A.eO&&this.a.N(0,b.a)&&A.zj(this)===A.zj(b)},
gI(a){return A.cL(this.a,A.zj(this),B.d,B.d,B.d,B.d,B.d,B.d,B.d,B.d)},
k(a){var s=B.b.aA([A.bc(this.$ti.c)],", ")
return this.a.k(0)+" with "+("<"+s+">")}}
A.eO.prototype={
$0(){return this.a.$1$0(this.$ti.y[0])},
$2(a,b){return this.a.$1$2(a,b,this.$ti.y[0])},
$S(){return A.I_(A.n_(this.a),this.$ti)}}
A.hM.prototype={}
A.te.prototype={
aM(a){var s,r,q=this,p=new RegExp(q.a).exec(a)
if(p==null)return null
s=Object.create(null)
r=q.b
if(r!==-1)s.arguments=p[r+1]
r=q.c
if(r!==-1)s.argumentsExpr=p[r+1]
r=q.d
if(r!==-1)s.expr=p[r+1]
r=q.e
if(r!==-1)s.method=p[r+1]
r=q.f
if(r!==-1)s.receiver=p[r+1]
return s}}
A.hC.prototype={
k(a){return"Null check operator used on a null value"}}
A.ka.prototype={
k(a){var s,r=this,q="NoSuchMethodError: method not found: '",p=r.b
if(p==null)return"NoSuchMethodError: "+r.a
s=r.c
if(s==null)return q+p+"' ("+r.a+")"
return q+p+"' on '"+s+"' ("+r.a+")"}}
A.lB.prototype={
k(a){var s=this.a
return s.length===0?"Error":"Error: "+s}}
A.ku.prototype={
k(a){return"Throw of null ('"+(this.a===null?"null":"undefined")+"' from JavaScript)"},
$iaj:1}
A.h9.prototype={}
A.iB.prototype={
k(a){var s,r=this.b
if(r!=null)return r
r=this.a
s=r!==null&&typeof r==="object"?r.stack:null
return this.b=s==null?"":s},
$iba:1}
A.bg.prototype={
k(a){var s=this.constructor,r=s==null?null:s.name
return"Closure '"+A.CP(r==null?"unknown":r)+"'"},
ga2(a){var s=A.n_(this)
return A.bc(s==null?A.aX(this):s)},
$icB:1,
gnq(){return this},
$C:"$1",
$R:1,
$D:null}
A.ju.prototype={$C:"$0",$R:0}
A.jv.prototype={$C:"$2",$R:2}
A.lq.prototype={}
A.lj.prototype={
k(a){var s=this.$static_name
if(s==null)return"Closure of unknown static method"
return"Closure '"+A.CP(s)+"'"}}
A.ex.prototype={
N(a,b){if(b==null)return!1
if(this===b)return!0
if(!(b instanceof A.ex))return!1
return this.$_target===b.$_target&&this.a===b.a},
gI(a){return(A.eo(this.a)^A.b0(this.$_target))>>>0},
k(a){return"Closure '"+this.$_name+"' of "+("Instance of '"+A.kF(this.a)+"'")}}
A.kR.prototype={
k(a){return"RuntimeError: "+this.a}}
A.bz.prototype={
gn(a){return this.a},
gL(a){return this.a===0},
ga1(a){return this.a!==0},
ga9(){return new A.aW(this,A.n(this).h("aW<1>"))},
gaF(){return new A.aC(this,A.n(this).h("aC<1,2>"))},
K(a){var s,r
if(typeof a=="string"){s=this.b
if(s==null)return!1
return s[a]!=null}else if(typeof a=="number"&&(a&0x3fffffff)===a){r=this.c
if(r==null)return!1
return r[a]!=null}else return this.hO(a)},
hO(a){var s=this.d
if(s==null)return!1
return this.bO(s[this.bN(a)],a)>=0},
B(a,b){A.n(this).h("L<1,2>").a(b).aa(0,new A.q0(this))},
j(a,b){var s,r,q,p,o=null
if(typeof b=="string"){s=this.b
if(s==null)return o
r=s[b]
q=r==null?o:r.b
return q}else if(typeof b=="number"&&(b&0x3fffffff)===b){p=this.c
if(p==null)return o
r=p[b]
q=r==null?o:r.b
return q}else return this.hP(b)},
hP(a){var s,r,q=this.d
if(q==null)return null
s=q[this.bN(a)]
r=this.bO(s,a)
if(r<0)return null
return s[r].b},
i(a,b,c){var s,r,q=this,p=A.n(q)
p.c.a(b)
p.y[1].a(c)
if(typeof b=="string"){s=q.b
q.fv(s==null?q.b=q.eo():s,b,c)}else if(typeof b=="number"&&(b&0x3fffffff)===b){r=q.c
q.fv(r==null?q.c=q.eo():r,b,c)}else q.hR(b,c)},
hR(a,b){var s,r,q,p,o=this,n=A.n(o)
n.c.a(a)
n.y[1].a(b)
s=o.d
if(s==null)s=o.d=o.eo()
r=o.bN(a)
q=s[r]
if(q==null)s[r]=[o.ep(a,b)]
else{p=o.bO(q,a)
if(p>=0)q[p].b=b
else q.push(o.ep(a,b))}},
dz(a,b){var s,r,q=this,p=A.n(q)
p.c.a(a)
p.h("2()").a(b)
if(q.K(a)){s=q.j(0,a)
return s==null?p.y[1].a(s):s}r=b.$0()
q.i(0,a,r)
return r},
J(a,b){var s=this
if(typeof b=="string")return s.hg(s.b,b)
else if(typeof b=="number"&&(b&0x3fffffff)===b)return s.hg(s.c,b)
else return s.hQ(b)},
hQ(a){var s,r,q,p,o=this,n=o.d
if(n==null)return null
s=o.bN(a)
r=n[s]
q=o.bO(r,a)
if(q<0)return null
p=r.splice(q,1)[0]
o.hs(p)
if(r.length===0)delete n[s]
return p.b},
O(a){var s=this
if(s.a>0){s.b=s.c=s.d=s.e=s.f=null
s.a=0
s.en()}},
aa(a,b){var s,r,q=this
A.n(q).h("~(1,2)").a(b)
s=q.e
r=q.r
while(s!=null){b.$2(s.a,s.b)
if(r!==q.r)throw A.d(A.aB(q))
s=s.c}},
fv(a,b,c){var s,r=A.n(this)
r.c.a(b)
r.y[1].a(c)
s=a[b]
if(s==null)a[b]=this.ep(b,c)
else s.b=c},
hg(a,b){var s
if(a==null)return null
s=a[b]
if(s==null)return null
this.hs(s)
delete a[b]
return s.b},
en(){this.r=this.r+1&1073741823},
ep(a,b){var s=this,r=A.n(s),q=new A.qd(r.c.a(a),r.y[1].a(b))
if(s.e==null)s.e=s.f=q
else{r=s.f
r.toString
q.d=r
s.f=r.c=q}++s.a
s.en()
return q},
hs(a){var s=this,r=a.d,q=a.c
if(r==null)s.e=q
else r.c=q
if(q==null)s.f=r
else q.d=r;--s.a
s.en()},
bN(a){return J.Z(a)&1073741823},
bO(a,b){var s,r
if(a==null)return-1
s=a.length
for(r=0;r<s;++r)if(J.a8(a[r].a,b))return r
return-1},
k(a){return A.qq(this)},
eo(){var s=Object.create(null)
s["<non-identifier-key>"]=s
delete s["<non-identifier-key>"]
return s},
$iqc:1}
A.q0.prototype={
$2(a,b){var s=this.a,r=A.n(s)
s.i(0,r.c.a(a),r.y[1].a(b))},
$S(){return A.n(this.a).h("~(1,2)")}}
A.qd.prototype={}
A.aW.prototype={
gn(a){return this.a.a},
gL(a){return this.a.a===0},
gC(a){var s=this.a
return new A.hv(s,s.r,s.e,this.$ti.h("hv<1>"))},
v(a,b){return this.a.K(b)}}
A.hv.prototype={
gu(){return this.d},
p(){var s,r=this,q=r.a
if(r.b!==q.r)throw A.d(A.aB(q))
s=r.c
if(s==null){r.d=null
return!1}else{r.d=s.a
r.c=s.c
return!0}},
$iab:1}
A.cI.prototype={
gn(a){return this.a.a},
gL(a){return this.a.a===0},
gC(a){var s=this.a
return new A.bh(s,s.r,s.e,this.$ti.h("bh<1>"))}}
A.bh.prototype={
gu(){return this.d},
p(){var s,r=this,q=r.a
if(r.b!==q.r)throw A.d(A.aB(q))
s=r.c
if(s==null){r.d=null
return!1}else{r.d=s.b
r.c=s.c
return!0}},
$iab:1}
A.aC.prototype={
gn(a){return this.a.a},
gL(a){return this.a.a===0},
gC(a){var s=this.a
return new A.cH(s,s.r,s.e,this.$ti.h("cH<1,2>"))}}
A.cH.prototype={
gu(){var s=this.d
s.toString
return s},
p(){var s,r=this,q=r.a
if(r.b!==q.r)throw A.d(A.aB(q))
s=r.c
if(s==null){r.d=null
return!1}else{r.d=new A.W(s.a,s.b,r.$ti.h("W<1,2>"))
r.c=s.c
return!0}},
$iab:1}
A.hs.prototype={
bN(a){return A.eo(a)&1073741823},
bO(a,b){var s,r,q
if(a==null)return-1
s=a.length
for(r=0;r<s;++r){q=a[r].a
if(q==null?b==null:q===b)return r}return-1}}
A.y2.prototype={
$1(a){return this.a(a)},
$S:44}
A.y3.prototype={
$2(a,b){return this.a(a,b)},
$S:92}
A.y4.prototype={
$1(a){return this.a(A.r(a))},
$S:115}
A.br.prototype={
ga2(a){return A.bc(this.fR())},
fR(){return A.Hk(this.$r,this.cS())},
k(a){return this.ho(!1)},
ho(a){var s,r,q,p,o,n=this.jY(),m=this.cS(),l=(a?"Record ":"")+"("
for(s=n.length,r="",q=0;q<s;++q,r=", "){l+=r
p=n[q]
if(typeof p=="string")l=l+p+": "
if(!(q<m.length))return A.f(m,q)
o=m[q]
l=a?l+A.AT(o):l+A.w(o)}l+=")"
return l.charCodeAt(0)==0?l:l},
jY(){var s,r=this.$s
while($.vS.length<=r)B.b.m($.vS,null)
s=$.vS[r]
if(s==null){s=this.jG()
B.b.i($.vS,r,s)}return s},
jG(){var s,r,q,p=this.$r,o=p.indexOf("("),n=p.substring(1,o),m=p.substring(o),l=m==="()"?0:m.replace(/[^,]/g,"").length+1,k=t.K,j=J.Au(l,k)
for(s=0;s<l;++s)j[s]=s
if(n!==""){r=n.split(",")
s=r.length
for(q=l;s>0;){--q;--s
B.b.i(j,q,r[s])}}return A.al(j,k)}}
A.fq.prototype={
cS(){return[this.a,this.b]},
N(a,b){if(b==null)return!1
return b instanceof A.fq&&this.$s===b.$s&&J.a8(this.a,b.a)&&J.a8(this.b,b.b)},
gI(a){return A.cL(this.$s,this.a,this.b,B.d,B.d,B.d,B.d,B.d,B.d,B.d)}}
A.eh.prototype={
cS(){return[this.a,this.b,this.c]},
N(a,b){var s=this
if(b==null)return!1
return b instanceof A.eh&&s.$s===b.$s&&J.a8(s.a,b.a)&&J.a8(s.b,b.b)&&J.a8(s.c,b.c)},
gI(a){var s=this
return A.cL(s.$s,s.a,s.b,s.c,B.d,B.d,B.d,B.d,B.d,B.d)}}
A.dx.prototype={
cS(){return this.a},
N(a,b){if(b==null)return!1
return b instanceof A.dx&&this.$s===b.$s&&A.Fy(this.a,b.a)},
gI(a){return A.cL(this.$s,A.AF(this.a),B.d,B.d,B.d,B.d,B.d,B.d,B.d,B.d)}}
A.dP.prototype={
k(a){return"RegExp/"+this.a+"/"+this.b.flags},
gfZ(){var s=this,r=s.c
if(r!=null)return r
r=s.b
return s.c=A.yK(s.a,r.multiline,!r.ignoreCase,r.unicode,r.dotAll,"g")},
gkp(){var s=this,r=s.d
if(r!=null)return r
r=s.b
return s.d=A.yK(s.a,r.multiline,!r.ignoreCase,r.unicode,r.dotAll,"y")},
mv(a){var s=this.b.exec(a)
if(s==null)return null
return new A.fp(s)},
d8(a,b,c){var s=b.length
if(c>s)throw A.d(A.an(c,0,s,null,null))
return new A.lP(this,b,c)},
bF(a,b){return this.d8(0,b,0)},
jT(a,b){var s,r=this.gfZ()
if(r==null)r=A.az(r)
r.lastIndex=b
s=r.exec(a)
if(s==null)return null
return new A.fp(s)},
jS(a,b){var s,r=this.gkp()
if(r==null)r=A.az(r)
r.lastIndex=b
s=r.exec(a)
if(s==null)return null
return new A.fp(s)},
bt(a,b,c){if(c<0||c>b.length)throw A.d(A.an(c,0,b.length,null,null))
return this.jS(b,c)},
mN(a,b){return this.bt(0,b,0)},
$iqI:1,
$iEG:1}
A.fp.prototype={
gG(){return this.b.index},
gF(){var s=this.b
return s.index+s[0].length},
j(a,b){var s=this.b
if(!(b<s.length))return A.f(s,b)
return s[b]},
mQ(a){var s,r=this.b.groups
if(r!=null){s=r[a]
if(s!=null||a in r)return s}throw A.d(A.dH(a,"name","Not a capture group name"))},
$icf:1,
$ihJ:1}
A.lP.prototype={
gC(a){return new A.ds(this.a,this.b,this.c)}}
A.ds.prototype={
gu(){var s=this.d
return s==null?t.F.a(s):s},
p(){var s,r,q,p,o,n,m=this,l=m.b
if(l==null)return!1
s=m.c
r=l.length
if(s<=r){q=m.a
p=q.jT(l,s)
if(p!=null){m.d=p
o=p.gF()
if(p.b.index===o){s=!1
if(q.b.unicode){q=m.c
n=q+1
if(n<r){if(!(q>=0&&q<r))return A.f(l,q)
q=l.charCodeAt(q)
if(q>=55296&&q<=56319){if(!(n>=0))return A.f(l,n)
s=l.charCodeAt(n)
s=s>=56320&&s<=57343}}}o=(s?o+1:o)+1}m.c=o
return!0}}m.b=m.d=null
return!1},
$iab:1}
A.hV.prototype={
gF(){return this.a+this.c.length},
j(a,b){if(b!==0)A.ak(A.qL(b,null))
return this.c},
$icf:1,
gG(){return this.a}}
A.mH.prototype={
gC(a){return new A.mI(this.a,this.b,this.c)}}
A.mI.prototype={
p(){var s,r,q=this,p=q.c,o=q.b,n=o.length,m=q.a,l=m.length
if(p+n>l){q.d=null
return!1}s=m.indexOf(o,p)
if(s<0){q.c=l+1
q.d=null
return!1}r=s+n
q.d=new A.hV(s,o)
q.c=r===q.c?r+1:r
return!0},
gu(){var s=this.d
s.toString
return s},
$iab:1}
A.uc.prototype={
hc(){var s=this.b
if(s===this)throw A.d(new A.cG("Local '' has not been initialized."))
return s},
shH(a){if(this.b!==this)throw A.d(new A.cG("Local '' has already been initialized."))
this.b=a}}
A.eY.prototype={
ga2(a){return B.k_},
$iag:1,
$iyv:1}
A.hz.prototype={
kh(a,b,c,d){var s=A.an(b,0,c,d,null)
throw A.d(s)},
fE(a,b,c,d){if(b>>>0!==b||b>c)this.kh(a,b,c,d)}}
A.kl.prototype={
ga2(a){return B.k0},
$iag:1,
$iyw:1}
A.b_.prototype={
gn(a){return a.length},
lh(a,b,c,d,e){var s,r,q=a.length
this.fE(a,b,q,"start")
this.fE(a,c,q,"end")
if(b>c)throw A.d(A.an(b,0,c,null,null))
s=c-b
if(e<0)throw A.d(A.ai(e,null))
r=d.length
if(r-e<s)throw A.d(A.cU("Not enough elements"))
if(e!==0||r!==s)d=d.subarray(e,e+s)
a.set(d,b)},
$iby:1}
A.hy.prototype={
j(a,b){A.d7(b,a,a.length)
return a[b]},
i(a,b,c){A.x9(c)
a.$flags&2&&A.au(a)
A.d7(b,a,a.length)
a[b]=c},
$iK:1,
$im:1,
$iq:1}
A.bA.prototype={
i(a,b,c){A.bb(c)
a.$flags&2&&A.au(a)
A.d7(b,a,a.length)
a[b]=c},
bg(a,b,c,d,e){t.fm.a(d)
a.$flags&2&&A.au(a,5)
if(t.aj.b(d)){this.lh(a,b,c,d,e)
return}this.iV(a,b,c,d,e)},
cC(a,b,c,d){return this.bg(a,b,c,d,0)},
$iK:1,
$im:1,
$iq:1}
A.km.prototype={
ga2(a){return B.k1},
$iag:1,
$ipe:1}
A.kn.prototype={
ga2(a){return B.k2},
$iag:1,
$ipf:1}
A.ko.prototype={
ga2(a){return B.k3},
j(a,b){A.d7(b,a,a.length)
return a[b]},
$iag:1,
$ipX:1}
A.kp.prototype={
ga2(a){return B.k4},
j(a,b){A.d7(b,a,a.length)
return a[b]},
$iag:1,
$ipY:1}
A.kq.prototype={
ga2(a){return B.k5},
j(a,b){A.d7(b,a,a.length)
return a[b]},
$iag:1,
$ipZ:1}
A.ks.prototype={
ga2(a){return B.k9},
j(a,b){A.d7(b,a,a.length)
return a[b]},
$iag:1,
$ith:1}
A.hA.prototype={
ga2(a){return B.ka},
j(a,b){A.d7(b,a,a.length)
return a[b]},
b4(a,b,c){return new Uint32Array(a.subarray(b,A.BX(b,c,a.length)))},
$iag:1,
$iti:1}
A.hB.prototype={
ga2(a){return B.kb},
gn(a){return a.length},
j(a,b){A.d7(b,a,a.length)
return a[b]},
$iag:1,
$itj:1}
A.dT.prototype={
ga2(a){return B.kc},
gn(a){return a.length},
j(a,b){A.d7(b,a,a.length)
return a[b]},
b4(a,b,c){return new Uint8Array(a.subarray(b,A.BX(b,c,a.length)))},
$iag:1,
$idT:1,
$ihX:1}
A.io.prototype={}
A.ip.prototype={}
A.iq.prototype={}
A.ir.prototype={}
A.c_.prototype={
h(a){return A.iK(v.typeUniverse,this,a)},
A(a){return A.BC(v.typeUniverse,this,a)}}
A.mf.prototype={}
A.mN.prototype={
k(a){return A.bk(this.a,null)},
$iB9:1}
A.m8.prototype={
k(a){return this.a}}
A.fu.prototype={$icY:1}
A.u5.prototype={
$1(a){var s=this.a,r=s.a
s.a=null
r.$0()},
$S:24}
A.u4.prototype={
$1(a){var s,r
this.a.a=t.M.a(a)
s=this.b
r=this.c
s.firstChild?s.removeChild(r):s.appendChild(r)},
$S:61}
A.u6.prototype={
$0(){this.a.$0()},
$S:8}
A.u7.prototype={
$0(){this.a.$0()},
$S:8}
A.mL.prototype={
j6(a,b){if(self.setTimeout!=null)this.b=self.setTimeout(A.fG(new A.wI(this,b),0),a)
else throw A.d(A.ao("`setTimeout()` not found."))},
W(){if(self.setTimeout!=null){var s=this.b
if(s==null)return
self.clearTimeout(s)
this.b=null}else throw A.d(A.ao("Canceling a timer."))},
$iF1:1}
A.wI.prototype={
$0(){this.a.b=null
this.b.$0()},
$S:0}
A.lT.prototype={
ba(a){var s,r=this,q=r.$ti
q.h("1/?").a(a)
if(a==null)a=q.c.a(a)
if(!r.b)r.a.bj(a)
else{s=r.a
if(q.h("ae<1>").b(a))s.fD(a)
else s.cP(a)}},
df(a,b){var s=this.a
if(this.b)s.an(new A.aG(a,b))
else s.c1(new A.aG(a,b))}}
A.xa.prototype={
$1(a){return this.a.$2(0,a)},
$S:25}
A.xb.prototype={
$2(a,b){this.a.$2(1,new A.h9(a,t.l.a(b)))},
$S:153}
A.xr.prototype={
$2(a,b){this.a(A.bb(a),b)},
$S:150}
A.d5.prototype={
gu(){var s=this.b
return s==null?this.$ti.c.a(s):s},
l2(a,b){var s,r,q
a=A.bb(a)
b=b
s=this.a
for(;;)try{r=s(this,a,b)
return r}catch(q){b=q
a=1}},
p(){var s,r,q,p,o=this,n=null,m=0
for(;;){s=o.d
if(s!=null)try{if(s.p()){o.b=s.gu()
return!0}else o.d=null}catch(r){n=r
m=1
o.d=null}q=o.l2(m,n)
if(1===q)return!0
if(0===q){o.b=null
p=o.e
if(p==null||p.length===0){o.a=A.Bx
return!1}if(0>=p.length)return A.f(p,-1)
o.a=p.pop()
m=0
n=null
continue}if(2===q){m=0
n=null
continue}if(3===q){n=o.c
o.c=null
p=o.e
if(p==null||p.length===0){o.b=null
o.a=A.Bx
throw n
return!1}if(0>=p.length)return A.f(p,-1)
o.a=p.pop()
m=1
continue}throw A.d(A.cU("sync*"))}return!1},
ns(a){var s,r,q=this
if(a instanceof A.d4){s=a.a()
r=q.e
if(r==null)r=q.e=[]
B.b.m(r,q.a)
q.a=s
return 2}else{q.d=J.aE(a)
return 2}},
$iab:1}
A.d4.prototype={
gC(a){return new A.d5(this.a(),this.$ti.h("d5<1>"))}}
A.aG.prototype={
k(a){return A.w(this.a)},
$iad:1,
gbh(){return this.b}}
A.aM.prototype={}
A.d0.prototype={
er(){},
es(){},
scU(a){this.ch=this.$ti.h("d0<1>?").a(a)},
seu(a){this.CW=this.$ti.h("d0<1>?").a(a)}}
A.i5.prototype={
gel(){return this.c<4},
kV(a){var s,r
A.n(this).h("d0<1>").a(a)
s=a.CW
r=a.ch
if(s==null)this.d=r
else s.scU(r)
if(r==null)this.e=s
else r.seu(s)
a.seu(a)
a.scU(a)},
cM(a,b,c,d){var s,r,q,p,o,n,m,l=this,k=A.n(l)
k.h("~(1)?").a(a)
t.Z.a(c)
if((l.c&4)!==0)return A.Bl(c,k.c)
s=$.a0
r=d?1:0
q=b!=null?32:0
t.bm.A(k.c).h("1(2)").a(a)
p=A.Bj(s,b)
o=c==null?A.Co():c
k=k.h("d0<1>")
n=new A.d0(l,a,p,t.M.a(o),s,r|q,k)
n.CW=n
n.ch=n
k.a(n)
n.ay=l.c&1
m=l.e
l.e=n
n.scU(null)
n.seu(m)
if(m==null)l.d=n
else m.scU(n)
if(l.d==l.e)A.mW(l.a)
return n},
hd(a){var s=this,r=A.n(s)
a=r.h("d0<1>").a(r.h("bj<1>").a(a))
if(a.ch===a)return null
r=a.ay
if((r&2)!==0)a.ay=r|4
else{s.kV(a)
if((s.c&2)===0&&s.d==null)s.jp()}return null},
he(a){A.n(this).h("bj<1>").a(a)},
hf(a){A.n(this).h("bj<1>").a(a)},
dY(){if((this.c&4)!==0)return new A.ck("Cannot add new events after calling close")
return new A.ck("Cannot add new events while doing an addStream")},
m(a,b){var s=this
A.n(s).c.a(b)
if(!s.gel())throw A.d(s.dY())
s.cY(b)},
hy(a){var s
if(!this.gel())throw A.d(this.dY())
s=A.zb(a,null)
this.d_(s.a,s.b)},
a_(){var s,r,q=this
if((q.c&4)!==0){s=q.r
s.toString
return s}if(!q.gel())throw A.d(q.dY())
q.c|=4
r=q.r
if(r==null)r=q.r=new A.a_($.a0,t.cU)
q.cZ()
return r},
jp(){if((this.c&4)!==0){var s=this.r
if((s.a&30)===0)s.bj(null)}A.mW(this.b)},
$ifd:1,
$iiD:1,
$ibP:1}
A.i3.prototype={
cY(a){var s,r=this.$ti
r.c.a(a)
for(s=this.d,r=r.h("c4<1>");s!=null;s=s.ch)s.b5(new A.c4(a,r))},
d_(a,b){var s
for(s=this.d;s!=null;s=s.ch)s.b5(new A.i9(a,b))},
cZ(){var s=this.d
if(s!=null)for(;s!=null;s=s.ch)s.b5(B.Y)
else this.r.bj(null)}}
A.pp.prototype={
$0(){this.c.a(null)
this.b.ea(null)},
$S:0}
A.pn.prototype={
$2(a,b){A.az(a)
t.l.a(b)
if(!this.a.b(a))throw A.d(a)
return this.c.$2(a,b)},
$S(){return this.d.h("0/(u,ba)")}}
A.pm.prototype={
$1(a){return this.a.a(a)},
$S(){return this.a.h("0(0)")}}
A.e5.prototype={
k(a){var s=this.b.k(0)
return"TimeoutException after "+s+": "+this.a},
$iaj:1}
A.po.prototype={
$1(a){var s,r,q,p,o,n,m,l=this
if(a===0){s=A.a([],l.c.h("D<0>"))
for(r=l.b,q=r.length,p=0;p<r.length;r.length===q||(0,A.I)(r),++p){o=r[p]
n=o.b
if(n==null)o.$ti.c.a(n)
s.push(n)}l.a.ba(s)}else{s=A.a([],t.fQ)
for(r=l.b,q=r.length,p=0;p<r.length;r.length===q||(0,A.I)(r),++p)s.push(r[p].c)
q=l.c
n=A.a([],q.h("D<0?>"))
for(m=r.length,p=0;p<r.length;r.length===m||(0,A.I)(r),++p)n.push(r[p].b)
l.a.bI(new A.hF(B.b.hJ(s,A.H1()),a,q.h("hF<q<0?>,q<aG?>>")))}},
$S:55}
A.hF.prototype={
k(a){var s,r,q="ParallelWaitError",p=this.c
if(p==null){p=this.d
s=p<=1
if(s)return q
return"ParallelWaitError("+p+" errors)"}s=this.d
r=s>1
if(r)s="("+s+" errors)"
else s=""
return q+s+": "+A.w(p.a)},
gbh(){var s=this.c
s=s==null?null:s.b
return s==null?A.ad.prototype.gbh.call(this):s}}
A.ig.prototype={
lF(a){t.lt.a(a)
this.a.b_(new A.uQ(this,a),new A.uR(this,a),t.a)}}
A.uQ.prototype={
$1(a){var s=this.a
s.b=s.$ti.c.a(a)
this.b.$1(0)},
$S(){return this.a.$ti.h("aa(1)")}}
A.uR.prototype={
$2(a,b){A.az(a)
t.l.a(b)
this.a.c=new A.aG(a,b)
this.b.$1(1)},
$S:13}
A.uP.prototype={
$1(a){var s=this.a,r=s.a+=a
if(++s.b===this.b.length)this.c.$1(r)},
$S:55}
A.fk.prototype={
df(a,b){A.az(a)
t.fw.a(b)
if((this.a.a&30)!==0)throw A.d(A.cU("Future already completed"))
this.an(A.zb(a,b))},
bI(a){return this.df(a,null)}}
A.c3.prototype={
ba(a){var s,r=this.$ti
r.h("1/?").a(a)
s=this.a
if((s.a&30)!==0)throw A.d(A.cU("Future already completed"))
s.bj(r.h("1/").a(a))},
m2(){return this.ba(null)},
an(a){this.a.c1(a)}}
A.iE.prototype={
ba(a){var s,r=this.$ti
r.h("1/?").a(a)
s=this.a
if((s.a&30)!==0)throw A.d(A.cU("Future already completed"))
s.ea(r.h("1/").a(a))},
an(a){this.a.an(a)}}
A.bE.prototype={
mO(a){if((this.c&15)!==6)return!0
return this.b.b.f8(t.iW.a(this.d),a.a,t.k4,t.K)},
mx(a){var s,r=this,q=r.e,p=null,o=t.z,n=t.K,m=a.a,l=r.b.b
if(t.ng.b(q))p=l.nc(q,m,a.b,o,n,t.l)
else p=l.f8(t.mq.a(q),m,o,n)
try{o=r.$ti.h("2/").a(p)
return o}catch(s){if(t.do.b(A.a1(s))){if((r.c&1)!==0)throw A.d(A.ai("The error handler of Future.then must return a value of the returned future's type","onError"))
throw A.d(A.ai("The error handler of Future.catchError must return a value of the future's type","onError"))}else throw s}}}
A.a_.prototype={
b_(a,b,c){var s,r,q,p=this.$ti
p.A(c).h("1/(2)").a(a)
s=$.a0
if(s===B.m){if(b!=null&&!t.ng.b(b)&&!t.mq.b(b))throw A.d(A.dH(b,"onError",u.c))}else{c.h("@<0/>").A(p.c).h("1(2)").a(a)
if(b!=null)b=A.Cb(b,s)}r=new A.a_(s,c.h("a_<0>"))
q=b==null?1:3
this.bz(new A.bE(r,q,a,b,p.h("@<1>").A(c).h("bE<1,2>")))
return r},
ah(a,b){return this.b_(a,null,b)},
hm(a,b,c){var s,r=this.$ti
r.A(c).h("1/(2)").a(a)
s=new A.a_($.a0,c.h("a_<0>"))
this.bz(new A.bE(s,19,a,b,r.h("@<1>").A(c).h("bE<1,2>")))
return s},
de(a){var s=this.$ti,r=$.a0,q=new A.a_(r,s)
if(r!==B.m)a=A.Cb(a,r)
this.bz(new A.bE(q,2,null,a,s.h("bE<1,1>")))
return q},
cu(a){var s,r
t.mY.a(a)
s=this.$ti
r=new A.a_($.a0,s)
this.bz(new A.bE(r,8,a,null,s.h("bE<1,1>")))
return r},
lf(a){this.a=this.a&1|16
this.c=a},
cO(a){this.a=a.a&30|this.a&1
this.c=a.c},
bz(a){var s,r=this,q=r.a
if(q<=3){a.a=t.np.a(r.c)
r.c=a}else{if((q&4)!==0){s=t.j_.a(r.c)
if((s.a&24)===0){s.bz(a)
return}r.cO(s)}A.fB(null,null,r.b,t.M.a(new A.uS(r,a)))}},
ha(a){var s,r,q,p,o,n,m=this,l={}
l.a=a
if(a==null)return
s=m.a
if(s<=3){r=t.np.a(m.c)
m.c=a
if(r!=null){q=a.a
for(p=a;q!=null;p=q,q=o)o=q.a
p.a=r}}else{if((s&4)!==0){n=t.j_.a(m.c)
if((n.a&24)===0){n.ha(a)
return}m.cO(n)}l.a=m.cX(a)
A.fB(null,null,m.b,t.M.a(new A.v_(l,m)))}},
cb(){var s=t.np.a(this.c)
this.c=null
return this.cX(s)},
cX(a){var s,r,q
for(s=a,r=null;s!=null;r=s,s=q){q=s.a
s.a=r}return r},
e4(a){var s,r,q,p=this
p.a^=2
try{a.b_(new A.uX(p),new A.uY(p),t.a)}catch(q){s=A.a1(q)
r=A.b3(q)
A.yi(new A.uZ(p,s,r))}},
ea(a){var s,r=this,q=r.$ti
q.h("1/").a(a)
if(q.h("ae<1>").b(a))if(a instanceof A.a_)A.uV(a,r,!0)
else r.e4(a)
else{s=r.cb()
q.c.a(a)
r.a=8
r.c=a
A.ea(r,s)}},
cP(a){var s,r=this
r.$ti.c.a(a)
s=r.cb()
r.a=8
r.c=a
A.ea(r,s)},
jF(a){var s,r,q=this
if((a.a&16)!==0){s=q.b===a.b
s=!(s||s)}else s=!1
if(s)return
r=q.cb()
q.cO(a)
A.ea(q,r)},
an(a){var s=this.cb()
this.lf(a)
A.ea(this,s)},
jE(a,b){A.az(a)
t.l.a(b)
this.an(new A.aG(a,b))},
bj(a){var s=this.$ti
s.h("1/").a(a)
if(s.h("ae<1>").b(a)){this.fD(a)
return}this.jf(a)},
jf(a){var s=this
s.$ti.c.a(a)
s.a^=2
A.fB(null,null,s.b,t.M.a(new A.uU(s,a)))},
fD(a){this.$ti.h("ae<1>").a(a)
if(a instanceof A.a_){A.uV(a,this,!1)
return}this.e4(a)},
c1(a){this.a^=2
A.fB(null,null,this.b,t.M.a(new A.uT(this,a)))},
nf(a,b){var s,r=this,q={}
if((r.a&24)!==0){q=new A.a_($.a0,r.$ti)
q.bj(r)
return q}s=new A.a_($.a0,r.$ti)
q.a=null
q.a=A.t8(a,new A.v5(s,a))
r.b_(new A.v6(q,r,s),new A.v7(q,s),t.a)
return s},
fb(a){return this.nf(a,null)},
$iae:1}
A.uS.prototype={
$0(){A.ea(this.a,this.b)},
$S:0}
A.v_.prototype={
$0(){A.ea(this.b,this.a.a)},
$S:0}
A.uX.prototype={
$1(a){var s,r,q,p,o,n=this.a
n.a^=2
try{n.cP(n.$ti.c.a(a))}catch(q){s=A.a1(q)
r=A.b3(q)
p=A.az(s)
o=t.l.a(r)
n.an(new A.aG(p,o))}},
$S:24}
A.uY.prototype={
$2(a,b){A.az(a)
t.l.a(b)
this.a.an(new A.aG(a,b))},
$S:13}
A.uZ.prototype={
$0(){this.a.an(new A.aG(this.b,this.c))},
$S:0}
A.uW.prototype={
$0(){A.uV(this.a.a,this.b,!0)},
$S:0}
A.uU.prototype={
$0(){this.a.cP(this.b)},
$S:0}
A.uT.prototype={
$0(){this.a.an(this.b)},
$S:0}
A.v2.prototype={
$0(){var s,r,q,p,o,n,m,l,k=this,j=null
try{q=k.a.a
j=q.b.b.i8(t.mY.a(q.d),t.z)}catch(p){s=A.a1(p)
r=A.b3(p)
if(k.c&&t.n.a(k.b.a.c).a===s){q=k.a
q.c=t.n.a(k.b.a.c)}else{q=s
o=r
if(o==null)o=A.ys(q)
n=k.a
n.c=new A.aG(q,o)
q=n}q.b=!0
return}if(j instanceof A.a_&&(j.a&24)!==0){if((j.a&16)!==0){q=k.a
q.c=t.n.a(j.c)
q.b=!0}return}if(t.g7.b(j)){m=k.b.a
l=new A.a_(m.b,m.$ti)
j.b_(new A.v3(l,m),new A.v4(l),t.H)
q=k.a
q.c=l
q.b=!1}},
$S:0}
A.v3.prototype={
$1(a){this.a.jF(this.b)},
$S:24}
A.v4.prototype={
$2(a,b){A.az(a)
t.l.a(b)
this.a.an(new A.aG(a,b))},
$S:13}
A.v1.prototype={
$0(){var s,r,q,p,o,n,m,l
try{q=this.a
p=q.a
o=p.$ti
n=o.c
m=n.a(this.b)
q.c=p.b.b.f8(o.h("2/(1)").a(p.d),m,o.h("2/"),n)}catch(l){s=A.a1(l)
r=A.b3(l)
q=s
p=r
if(p==null)p=A.ys(q)
o=this.a
o.c=new A.aG(q,p)
o.b=!0}},
$S:0}
A.v0.prototype={
$0(){var s,r,q,p,o,n,m,l=this
try{s=t.n.a(l.a.a.c)
p=l.b
if(p.a.mO(s)&&p.a.e!=null){p.c=p.a.mx(s)
p.b=!1}}catch(o){r=A.a1(o)
q=A.b3(o)
p=t.n.a(l.a.a.c)
if(p.a===r){n=l.b
n.c=p
p=n}else{p=r
n=q
if(n==null)n=A.ys(p)
m=l.b
m.c=new A.aG(p,n)
p=m}p.b=!0}},
$S:0}
A.v5.prototype={
$0(){var s=A.B6()
this.a.an(new A.aG(new A.e5("Future not completed",this.b),s))},
$S:0}
A.v6.prototype={
$1(a){var s
this.b.$ti.c.a(a)
s=this.a.a
if(s.b!=null){s.W()
this.c.cP(a)}},
$S(){return this.b.$ti.h("aa(1)")}}
A.v7.prototype={
$2(a,b){var s
A.az(a)
t.l.a(b)
s=this.a.a
if(s.b!=null){s.W()
this.b.an(new A.aG(a,b))}},
$S:13}
A.lU.prototype={}
A.aH.prototype={
gn(a){var s={},r=new A.a_($.a0,t.hy)
s.a=0
this.aY(new A.t_(s,this),!0,new A.t0(s,r),r.gjD())
return r}}
A.t_.prototype={
$1(a){A.n(this.b).h("aH.T").a(a);++this.a.a},
$S(){return A.n(this.b).h("~(aH.T)")}}
A.t0.prototype={
$0(){this.b.ea(this.a.a)},
$S:0}
A.e2.prototype={
aY(a,b,c,d){return this.a.aY(A.n(this).h("~(e2.T)?").a(a),!0,t.Z.a(c),d)}}
A.fr.prototype={
gkC(){var s,r=this
if((r.b&8)===0)return A.n(r).h("c6<1>?").a(r.a)
s=A.n(r)
return s.h("c6<1>?").a(s.h("iC<1>").a(r.a).gbE())},
fM(){var s,r,q=this
if((q.b&8)===0){s=q.a
if(s==null)s=q.a=new A.c6(A.n(q).h("c6<1>"))
return A.n(q).h("c6<1>").a(s)}r=A.n(q)
s=r.h("iC<1>").a(q.a).gbE()
return r.h("c6<1>").a(s)},
geD(){var s=this.a
if((this.b&8)!==0)s=t.gL.a(s).gbE()
return A.n(this).h("d1<1>").a(s)},
cN(){if((this.b&4)!==0)return new A.ck("Cannot add event after closing")
return new A.ck("Cannot add event while adding a stream")},
fL(){var s=this.c
if(s==null)s=this.c=(this.b&2)!==0?$.iY():new A.a_($.a0,t.cU)
return s},
a_(){var s=this,r=s.b
if((r&4)!==0)return s.fL()
if(r>=4)throw A.d(s.cN())
s.fF()
return s.fL()},
fF(){var s=this.b|=4
if((s&1)!==0)this.geD().b5(B.Y)
else if((s&3)===0)this.fM().m(0,B.Y)},
dZ(a){var s,r=this,q=A.n(r)
q.c.a(a)
s=r.b
if((s&1)!==0){q.c.a(a)
r.geD().b5(new A.c4(a,q.h("c4<1>")))}else if((s&3)===0)r.fM().m(0,new A.c4(a,q.h("c4<1>")))},
cM(a,b,c,d){var s,r,q,p,o=this,n=A.n(o)
n.h("~(1)?").a(a)
t.Z.a(c)
if((o.b&3)!==0)throw A.d(A.cU("Stream has already been listened to."))
s=A.Fh(o,a,b,c,d,n.c)
r=o.gkC()
if(((o.b|=1)&8)!==0){q=n.h("iC<1>").a(o.a)
q.sbE(s)
q.na()}else o.a=s
s.lg(r)
n=t.M.a(new A.wH(o))
p=s.e
s.e=p|64
n.$0()
s.e&=4294967231
s.e6((p&4)!==0)
return s},
hd(a){var s,r,q,p,o,n,m,l,k=this,j=A.n(k)
j.h("bj<1>").a(a)
s=null
if((k.b&8)!==0)s=j.h("iC<1>").a(k.a).W()
k.a=null
k.b=k.b&4294967286|2
r=k.r
if(r!=null)if(s==null)try{q=r.$0()
if(t.p8.b(q))s=q}catch(n){p=A.a1(n)
o=A.b3(n)
m=new A.a_($.a0,t.cU)
j=A.az(p)
l=t.l.a(o)
m.c1(new A.aG(j,l))
s=m}else s=s.cu(r)
j=new A.wG(k)
if(s!=null)s=s.cu(j)
else j.$0()
return s},
he(a){var s=this,r=A.n(s)
r.h("bj<1>").a(a)
if((s.b&8)!==0)r.h("iC<1>").a(s.a).nx()
A.mW(s.e)},
hf(a){var s=this,r=A.n(s)
r.h("bj<1>").a(a)
if((s.b&8)!==0)r.h("iC<1>").a(s.a).na()
A.mW(s.f)},
smU(a){this.d=t.Z.a(a)},
smV(a){this.f=t.Z.a(a)},
smT(a){this.r=t.Z.a(a)},
$ifd:1,
$iiD:1,
$ibP:1}
A.wH.prototype={
$0(){A.mW(this.a.d)},
$S:0}
A.wG.prototype={
$0(){var s=this.a.c
if(s!=null&&(s.a&30)===0)s.bj(null)},
$S:0}
A.i4.prototype={}
A.dt.prototype={}
A.dv.prototype={
gI(a){return(A.b0(this.a)^892482866)>>>0},
N(a,b){if(b==null)return!1
if(this===b)return!0
return b instanceof A.dv&&b.a===this.a}}
A.d1.prototype={
h1(){return this.w.hd(this)},
er(){this.w.he(this)},
es(){this.w.hf(this)}}
A.fj.prototype={
lg(a){var s=this
A.n(s).h("c6<1>?").a(a)
if(a==null)return
s.r=a
if(a.c!=null){s.e|=128
a.dQ(s)}},
W(){if(((this.e&=4294967279)&8)===0)this.e3()
var s=this.f
return s==null?$.iY():s},
e3(){var s,r=this,q=r.e|=8
if((q&128)!==0){s=r.r
if(s.a===1)s.a=3}if((q&64)===0)r.r=null
r.f=r.h1()},
dZ(a){var s,r=this,q=A.n(r)
q.c.a(a)
s=r.e
if((s&8)!==0)return
if(s<64)r.cY(a)
else r.b5(new A.c4(a,q.h("c4<1>")))},
ja(a,b){var s
if(t.B.b(a))A.yQ(a,b)
s=this.e
if((s&8)!==0)return
if(s<64)this.d_(a,b)
else this.b5(new A.i9(a,b))},
je(){var s=this,r=s.e
if((r&8)!==0)return
r|=2
s.e=r
if(r<64)s.cZ()
else s.b5(B.Y)},
er(){},
es(){},
h1(){return null},
b5(a){var s,r=this,q=r.r
if(q==null)q=r.r=new A.c6(A.n(r).h("c6<1>"))
q.m(0,a)
s=r.e
if((s&128)===0){s|=128
r.e=s
if(s<256)q.dQ(r)}},
cY(a){var s,r=this,q=A.n(r).c
q.a(a)
s=r.e
r.e=s|64
r.d.f9(r.a,a,q)
r.e&=4294967231
r.e6((s&4)!==0)},
d_(a,b){var s,r=this,q=r.e,p=new A.ua(r,a,b)
if((q&1)!==0){r.e=q|16
r.e3()
s=r.f
if(s!=null&&s!==$.iY())s.cu(p)
else p.$0()}else{p.$0()
r.e6((q&4)!==0)}},
cZ(){var s,r=this,q=new A.u9(r)
r.e3()
r.e|=16
s=r.f
if(s!=null&&s!==$.iY())s.cu(q)
else q.$0()},
e6(a){var s,r,q=this,p=q.e
if((p&128)!==0&&q.r.c==null){p=q.e=p&4294967167
s=!1
if((p&4)!==0)if(p<256){s=q.r
s=s==null?null:s.c==null
s=s!==!1}if(s){p&=4294967291
q.e=p}}for(;;a=r){if((p&8)!==0){q.r=null
return}r=(p&4)!==0
if(a===r)break
q.e=p^64
if(r)q.er()
else q.es()
p=q.e&=4294967231}if((p&128)!==0&&p<256)q.r.dQ(q)},
$ibj:1,
$ibP:1}
A.ua.prototype={
$0(){var s,r,q,p=this.a,o=p.e
if((o&8)!==0&&(o&16)===0)return
p.e=o|64
s=p.b
o=this.b
r=t.K
q=p.d
if(t.b9.b(s))q.nd(s,o,this.c,r,t.l)
else q.f9(t.i6.a(s),o,r)
p.e&=4294967231},
$S:0}
A.u9.prototype={
$0(){var s=this.a,r=s.e
if((r&16)===0)return
s.e=r|74
s.d.f7(s.c)
s.e&=4294967231},
$S:0}
A.fs.prototype={
aY(a,b,c,d){var s=A.n(this)
s.h("~(1)?").a(a)
t.Z.a(c)
return this.a.cM(s.h("~(1)?").a(a),d,c,b===!0)},
bP(a){return this.aY(a,null,null,null)},
hT(a,b,c){return this.aY(a,null,b,c)}}
A.d2.prototype={
scm(a){this.a=t.nf.a(a)},
gcm(){return this.a}}
A.c4.prototype={
f2(a){this.$ti.h("bP<1>").a(a).cY(this.b)}}
A.i9.prototype={
f2(a){a.d_(this.b,this.c)}}
A.m0.prototype={
f2(a){a.cZ()},
gcm(){return null},
scm(a){throw A.d(A.cU("No events after a done."))},
$id2:1}
A.c6.prototype={
dQ(a){var s,r=this
r.$ti.h("bP<1>").a(a)
s=r.a
if(s===1)return
if(s>=1){r.a=1
return}A.yi(new A.vR(r,a))
r.a=1},
m(a,b){var s=this,r=s.c
if(r==null)s.b=s.c=b
else{r.scm(b)
s.c=b}}}
A.vR.prototype={
$0(){var s,r,q,p=this.a,o=p.a
p.a=0
if(o===3)return
s=p.$ti.h("bP<1>").a(this.b)
r=p.b
q=r.gcm()
p.b=q
if(q==null)p.c=null
r.f2(s)},
$S:0}
A.fl.prototype={
W(){this.a=-1
this.c=null
return $.iY()},
kv(){var s,r=this,q=r.a-1
if(q===0){r.a=-1
s=r.c
if(s!=null){r.c=null
r.b.f7(s)}}else r.a=q},
$ibj:1}
A.mG.prototype={}
A.ib.prototype={
aY(a,b,c,d){var s=this.$ti
s.h("~(1)?").a(a)
return A.Bl(t.Z.a(c),s.c)}}
A.il.prototype={
aY(a,b,c,d){var s,r=null,q=this.$ti
q.h("~(1)?").a(a)
t.Z.a(c)
s=new A.im(r,r,r,r,q.h("im<1>"))
s.smU(new A.vE(this,s))
return s.cM(a,d,c,!0)}}
A.vE.prototype={
$0(){this.a.b.$1(this.b)},
$S:0}
A.im.prototype={
m0(){var s=this,r=s.b
if((r&4)!==0)return
if(r>=4)throw A.d(s.cN())
r|=4
s.b=r
if((r&1)!==0)s.geD().je()},
$ikk:1}
A.iP.prototype={$iBh:1}
A.mC.prototype={
f7(a){var s,r,q
t.M.a(a)
try{if(B.m===$.a0){a.$0()
return}A.Cd(null,null,this,a,t.H)}catch(q){s=A.a1(q)
r=A.b3(q)
A.fA(A.az(s),t.l.a(r))}},
f9(a,b,c){var s,r,q
c.h("~(0)").a(a)
c.a(b)
try{if(B.m===$.a0){a.$1(b)
return}A.Cf(null,null,this,a,b,t.H,c)}catch(q){s=A.a1(q)
r=A.b3(q)
A.fA(A.az(s),t.l.a(r))}},
nd(a,b,c,d,e){var s,r,q
d.h("@<0>").A(e).h("~(1,2)").a(a)
d.a(b)
e.a(c)
try{if(B.m===$.a0){a.$2(b,c)
return}A.Ce(null,null,this,a,b,c,t.H,d,e)}catch(q){s=A.a1(q)
r=A.b3(q)
A.fA(A.az(s),t.l.a(r))}},
eJ(a){return new A.vU(this,t.M.a(a))},
lU(a,b){return new A.vV(this,b.h("~(0)").a(a),b)},
i8(a,b){b.h("0()").a(a)
if($.a0===B.m)return a.$0()
return A.Cd(null,null,this,a,b)},
f8(a,b,c,d){c.h("@<0>").A(d).h("1(2)").a(a)
d.a(b)
if($.a0===B.m)return a.$1(b)
return A.Cf(null,null,this,a,b,c,d)},
nc(a,b,c,d,e,f){d.h("@<0>").A(e).A(f).h("1(2,3)").a(a)
e.a(b)
f.a(c)
if($.a0===B.m)return a.$2(b,c)
return A.Ce(null,null,this,a,b,c,d,e,f)},
dC(a,b,c,d){return b.h("@<0>").A(c).A(d).h("1(2,3)").a(a)}}
A.vU.prototype={
$0(){return this.a.f7(this.b)},
$S:0}
A.vV.prototype={
$1(a){var s=this.c
return this.a.f9(this.b,s.a(a),s)},
$S(){return this.c.h("~(0)")}}
A.xo.prototype={
$0(){A.Ar(this.a,this.b)},
$S:0}
A.eb.prototype={
gn(a){return this.a},
gL(a){return this.a===0},
ga1(a){return this.a!==0},
ga9(){return new A.ii(this,A.n(this).h("ii<1>"))},
K(a){var s,r
if(typeof a=="string"&&a!=="__proto__"){s=this.b
return s==null?!1:s[a]!=null}else if(typeof a=="number"&&(a&1073741823)===a){r=this.c
return r==null?!1:r[a]!=null}else return this.jL(a)},
jL(a){var s=this.d
if(s==null)return!1
return this.aw(this.fQ(s,a),a)>=0},
B(a,b){A.n(this).h("L<1,2>").a(b).aa(0,new A.vc(this))},
j(a,b){var s,r,q
if(typeof b=="string"&&b!=="__proto__"){s=this.b
r=s==null?null:A.Bo(s,b)
return r}else if(typeof b=="number"&&(b&1073741823)===b){q=this.c
r=q==null?null:A.Bo(q,b)
return r}else return this.k5(b)},
k5(a){var s,r,q=this.d
if(q==null)return null
s=this.fQ(q,a)
r=this.aw(s,a)
return r<0?null:s[r+1]},
i(a,b,c){var s,r,q=this,p=A.n(q)
p.c.a(b)
p.y[1].a(c)
if(typeof b=="string"&&b!=="__proto__"){s=q.b
q.fH(s==null?q.b=A.z_():s,b,c)}else if(typeof b=="number"&&(b&1073741823)===b){r=q.c
q.fH(r==null?q.c=A.z_():r,b,c)}else q.le(b,c)},
le(a,b){var s,r,q,p,o=this,n=A.n(o)
n.c.a(a)
n.y[1].a(b)
s=o.d
if(s==null)s=o.d=A.z_()
r=o.aD(a)
q=s[r]
if(q==null){A.z0(s,r,[a,b]);++o.a
o.e=null}else{p=o.aw(q,a)
if(p>=0)q[p+1]=b
else{q.push(a,b);++o.a
o.e=null}}},
J(a,b){var s=this.ew(b)
return s},
ew(a){var s,r,q,p,o=this,n=o.d
if(n==null)return null
s=o.aD(a)
r=n[s]
q=o.aw(r,a)
if(q<0)return null;--o.a
o.e=null
p=r.splice(q,2)[1]
if(0===r.length)delete n[s]
return p},
aa(a,b){var s,r,q,p,o,n,m=this,l=A.n(m)
l.h("~(1,2)").a(b)
s=m.ec()
for(r=s.length,q=l.c,l=l.y[1],p=0;p<r;++p){o=s[p]
q.a(o)
n=m.j(0,o)
b.$2(o,n==null?l.a(n):n)
if(s!==m.e)throw A.d(A.aB(m))}},
ec(){var s,r,q,p,o,n,m,l,k,j,i=this,h=i.e
if(h!=null)return h
h=A.bL(i.a,null,!1,t.z)
s=i.b
r=0
if(s!=null){q=Object.getOwnPropertyNames(s)
p=q.length
for(o=0;o<p;++o){h[r]=q[o];++r}}n=i.c
if(n!=null){q=Object.getOwnPropertyNames(n)
p=q.length
for(o=0;o<p;++o){h[r]=+q[o];++r}}m=i.d
if(m!=null){q=Object.getOwnPropertyNames(m)
p=q.length
for(o=0;o<p;++o){l=m[q[o]]
k=l.length
for(j=0;j<k;j+=2){h[r]=l[j];++r}}}return i.e=h},
fH(a,b,c){var s=A.n(this)
s.c.a(b)
s.y[1].a(c)
if(a[b]==null){++this.a
this.e=null}A.z0(a,b,c)},
aD(a){return J.Z(a)&1073741823},
fQ(a,b){return a[this.aD(b)]},
aw(a,b){var s,r
if(a==null)return-1
s=a.length
for(r=0;r<s;r+=2)if(J.a8(a[r],b))return r
return-1}}
A.vc.prototype={
$2(a,b){var s=this.a,r=A.n(s)
s.i(0,r.c.a(a),r.y[1].a(b))},
$S(){return A.n(this.a).h("~(1,2)")}}
A.fo.prototype={
aD(a){return A.eo(a)&1073741823},
aw(a,b){var s,r,q
if(a==null)return-1
s=a.length
for(r=0;r<s;r+=2){q=a[r]
if(q==null?b==null:q===b)return r}return-1}}
A.ii.prototype={
gn(a){return this.a.a},
gL(a){return this.a.a===0},
ga1(a){return this.a.a!==0},
gC(a){var s=this.a
return new A.ec(s,s.ec(),this.$ti.h("ec<1>"))},
v(a,b){return this.a.K(b)}}
A.ec.prototype={
gu(){var s=this.d
return s==null?this.$ti.c.a(s):s},
p(){var s=this,r=s.b,q=s.c,p=s.a
if(r!==p.e)throw A.d(A.aB(p))
else if(q>=r.length){s.d=null
return!1}else{s.d=r[q]
s.c=q+1
return!0}},
$iab:1}
A.ik.prototype={
j(a,b){if(!this.y.$1(b))return null
return this.iP(b)},
i(a,b,c){var s=this.$ti
this.iR(s.c.a(b),s.y[1].a(c))},
K(a){if(!this.y.$1(a))return!1
return this.iO(a)},
J(a,b){if(!this.y.$1(b))return null
return this.iQ(b)},
bN(a){return this.x.$1(this.$ti.c.a(a))&1073741823},
bO(a,b){var s,r,q,p
if(a==null)return-1
s=a.length
for(r=this.$ti.c,q=this.w,p=0;p<s;++p)if(q.$2(r.a(a[p].a),r.a(b)))return p
return-1}}
A.vr.prototype={
$1(a){return this.a.b(a)},
$S:45}
A.ed.prototype={
h_(){return new A.ed(A.n(this).h("ed<1>"))},
gC(a){return new A.d3(this,this.eb(),A.n(this).h("d3<1>"))},
gn(a){return this.a},
gL(a){return this.a===0},
ga1(a){return this.a!==0},
v(a,b){var s,r
if(typeof b=="string"&&b!=="__proto__"){s=this.b
return s==null?!1:s[b]!=null}else{r=this.ed(b)
return r}},
ed(a){var s=this.d
if(s==null)return!1
return this.aw(s[this.aD(a)],a)>=0},
m(a,b){var s,r,q=this
A.n(q).c.a(b)
if(typeof b=="string"&&b!=="__proto__"){s=q.b
return q.c3(s==null?q.b=A.z1():s,b)}else if(typeof b=="number"&&(b&1073741823)===b){r=q.c
return q.c3(r==null?q.c=A.z1():r,b)}else return q.dX(b)},
dX(a){var s,r,q,p=this
A.n(p).c.a(a)
s=p.d
if(s==null)s=p.d=A.z1()
r=p.aD(a)
q=s[r]
if(q==null)s[r]=[a]
else{if(p.aw(q,a)>=0)return!1
q.push(a)}++p.a
p.e=null
return!0},
O(a){var s=this
if(s.a>0){s.b=s.c=s.d=s.e=null
s.a=0}},
eb(){var s,r,q,p,o,n,m,l,k,j,i=this,h=i.e
if(h!=null)return h
h=A.bL(i.a,null,!1,t.z)
s=i.b
r=0
if(s!=null){q=Object.getOwnPropertyNames(s)
p=q.length
for(o=0;o<p;++o){h[r]=q[o];++r}}n=i.c
if(n!=null){q=Object.getOwnPropertyNames(n)
p=q.length
for(o=0;o<p;++o){h[r]=+q[o];++r}}m=i.d
if(m!=null){q=Object.getOwnPropertyNames(m)
p=q.length
for(o=0;o<p;++o){l=m[q[o]]
k=l.length
for(j=0;j<k;++j){h[r]=l[j];++r}}}return i.e=h},
c3(a,b){A.n(this).c.a(b)
if(a[b]!=null)return!1
a[b]=0;++this.a
this.e=null
return!0},
aD(a){return J.Z(a)&1073741823},
aw(a,b){var s,r
if(a==null)return-1
s=a.length
for(r=0;r<s;++r)if(J.a8(a[r],b))return r
return-1}}
A.d3.prototype={
gu(){var s=this.d
return s==null?this.$ti.c.a(s):s},
p(){var s=this,r=s.b,q=s.c,p=s.a
if(r!==p.e)throw A.d(A.aB(p))
else if(q>=r.length){s.d=null
return!1}else{s.d=r[q]
s.c=q+1
return!0}},
$iab:1}
A.c5.prototype={
h_(){return new A.c5(A.n(this).h("c5<1>"))},
gC(a){var s=this,r=new A.ef(s,s.r,A.n(s).h("ef<1>"))
r.c=s.e
return r},
gn(a){return this.a},
gL(a){return this.a===0},
ga1(a){return this.a!==0},
v(a,b){var s,r
if(typeof b=="string"&&b!=="__proto__"){s=this.b
if(s==null)return!1
return t.nF.a(s[b])!=null}else if(typeof b=="number"&&(b&1073741823)===b){r=this.c
if(r==null)return!1
return t.nF.a(r[b])!=null}else return this.ed(b)},
ed(a){var s=this.d
if(s==null)return!1
return this.aw(s[this.aD(a)],a)>=0},
m(a,b){var s,r,q=this
A.n(q).c.a(b)
if(typeof b=="string"&&b!=="__proto__"){s=q.b
return q.c3(s==null?q.b=A.z2():s,b)}else if(typeof b=="number"&&(b&1073741823)===b){r=q.c
return q.c3(r==null?q.c=A.z2():r,b)}else return q.dX(b)},
dX(a){var s,r,q,p=this
A.n(p).c.a(a)
s=p.d
if(s==null)s=p.d=A.z2()
r=p.aD(a)
q=s[r]
if(q==null)s[r]=[p.e8(a)]
else{if(p.aw(q,a)>=0)return!1
q.push(p.e8(a))}return!0},
J(a,b){var s=this
if(typeof b=="string"&&b!=="__proto__")return s.fI(s.b,b)
else if(typeof b=="number"&&(b&1073741823)===b)return s.fI(s.c,b)
else return s.ew(b)},
ew(a){var s,r,q,p,o=this,n=o.d
if(n==null)return!1
s=o.aD(a)
r=n[s]
q=o.aw(r,a)
if(q<0)return!1
p=r.splice(q,1)[0]
if(0===r.length)delete n[s]
o.fJ(p)
return!0},
O(a){var s=this
if(s.a>0){s.b=s.c=s.d=s.e=s.f=null
s.a=0
s.e7()}},
c3(a,b){A.n(this).c.a(b)
if(t.nF.a(a[b])!=null)return!1
a[b]=this.e8(b)
return!0},
fI(a,b){var s
if(a==null)return!1
s=t.nF.a(a[b])
if(s==null)return!1
this.fJ(s)
delete a[b]
return!0},
e7(){this.r=this.r+1&1073741823},
e8(a){var s,r=this,q=new A.mq(A.n(r).c.a(a))
if(r.e==null)r.e=r.f=q
else{s=r.f
s.toString
q.c=s
r.f=s.b=q}++r.a
r.e7()
return q},
fJ(a){var s=this,r=a.c,q=a.b
if(r==null)s.e=q
else r.b=q
if(q==null)s.f=r
else q.c=r;--s.a
s.e7()},
aD(a){return J.Z(a)&1073741823},
aw(a,b){var s,r
if(a==null)return-1
s=a.length
for(r=0;r<s;++r)if(J.a8(a[r].a,b))return r
return-1},
$iAA:1}
A.mq.prototype={}
A.ef.prototype={
gu(){var s=this.d
return s==null?this.$ti.c.a(s):s},
p(){var s=this,r=s.c,q=s.a
if(s.b!==q.r)throw A.d(A.aB(q))
else if(r==null){s.d=null
return!1}else{s.d=s.$ti.h("1?").a(r.a)
s.c=r.b
return!0}},
$iab:1}
A.qf.prototype={
$2(a,b){this.a.i(0,this.b.a(a),this.c.a(b))},
$S:148}
A.T.prototype={
gC(a){return new A.aw(a,this.gn(a),A.aX(a).h("aw<T.E>"))},
X(a,b){return this.j(a,b)},
gL(a){return this.gn(a)===0},
ga1(a){return!this.gL(a)},
v(a,b){var s,r=this.gn(a)
for(s=0;s<r;++s){if(J.a8(this.j(a,s),b))return!0
if(r!==this.gn(a))throw A.d(A.aB(a))}return!1},
dJ(a,b){var s=A.aX(a)
return new A.a3(a,s.h("y(T.E)").a(b),s.h("a3<T.E>"))},
aZ(a,b,c){var s=A.aX(a)
return new A.E(a,s.A(c).h("1(T.E)").a(b),s.h("@<T.E>").A(c).h("E<1,2>"))},
aC(a,b){return A.e4(a,b,null,A.aX(a).h("T.E"))},
m(a,b){var s
A.aX(a).h("T.E").a(b)
s=this.gn(a)
this.sn(a,s+1)
this.i(a,s,b)},
cc(a,b){return new A.cw(a,A.aX(a).h("@<T.E>").A(b).h("cw<1,2>"))},
ai(a,b){var s,r=A.aX(a)
r.h("h(T.E,T.E)?").a(b)
s=b==null?A.H7():b
A.lb(a,0,this.gn(a)-1,s,r.h("T.E"))},
mt(a,b,c,d){var s
A.aX(a).h("T.E?").a(d)
A.ch(b,c,this.gn(a))
for(s=b;s<c;++s)this.i(a,s,d)},
bg(a,b,c,d,e){var s,r,q,p,o
A.aX(a).h("m<T.E>").a(d)
A.ch(b,c,this.gn(a))
s=c-b
if(s===0)return
A.bo(e,"skipCount")
if(t._.b(d)){r=e
q=d}else{q=J.ns(d,e).b0(0,!1)
r=0}p=J.aT(q)
if(r+s>p.gn(q))throw A.d(A.At())
if(r<b)for(o=s-1;o>=0;--o)this.i(a,b+o,p.j(q,r+o))
else for(o=0;o<s;++o)this.i(a,b+o,p.j(q,r+o))},
bM(a,b){var s
A.aX(a).h("y(T.E)").a(b)
for(s=0;s<this.gn(a);++s)if(b.$1(this.j(a,s)))return s
return-1},
k(a){return A.yI(a,"[","]")},
$iK:1,
$im:1,
$iq:1}
A.a5.prototype={
aa(a,b){var s,r,q,p=A.n(this)
p.h("~(a5.K,a5.V)").a(b)
for(s=this.ga9(),s=s.gC(s),p=p.h("a5.V");s.p();){r=s.gu()
q=this.j(0,r)
b.$2(r,q==null?p.a(q):q)}},
ic(a){var s,r,q,p=this,o=A.n(p)
o.h("a5.V(a5.K,a5.V)").a(a)
for(s=p.ga9(),s=s.gC(s),o=o.h("a5.V");s.p();){r=s.gu()
q=p.j(0,r)
p.i(0,r,a.$2(r,q==null?o.a(q):q))}},
gaF(){return this.ga9().aZ(0,new A.qp(this),A.n(this).h("W<a5.K,a5.V>"))},
bs(a,b,c,d){var s,r,q,p,o,n=A.n(this)
n.A(c).A(d).h("W<1,2>(a5.K,a5.V)").a(b)
s=A.t(c,d)
for(r=this.ga9(),r=r.gC(r),n=n.h("a5.V");r.p();){q=r.gu()
p=this.j(0,q)
o=b.$2(q,p==null?n.a(p):p)
s.i(0,o.a,o.b)}return s},
K(a){return this.ga9().v(0,a)},
gn(a){var s=this.ga9()
return s.gn(s)},
gL(a){var s=this.ga9()
return s.gL(s)},
ga1(a){var s=this.ga9()
return s.ga1(s)},
k(a){return A.qq(this)},
$iL:1}
A.qp.prototype={
$1(a){var s=this.a,r=A.n(s)
r.h("a5.K").a(a)
s=s.j(0,a)
if(s==null)s=r.h("a5.V").a(s)
return new A.W(a,s,r.h("W<a5.K,a5.V>"))},
$S(){return A.n(this.a).h("W<a5.K,a5.V>(a5.K)")}}
A.qr.prototype={
$2(a,b){var s,r=this.a
if(!r.a)this.b.a+=", "
r.a=!1
r=this.b
s=A.w(a)
r.a=(r.a+=s)+": "
s=A.w(b)
r.a+=s},
$S:51}
A.iL.prototype={
i(a,b,c){var s=A.n(this)
s.c.a(b)
s.y[1].a(c)
throw A.d(A.ao("Cannot modify unmodifiable map"))}}
A.eU.prototype={
j(a,b){return this.a.j(0,b)},
i(a,b,c){var s=A.n(this)
this.a.i(0,s.c.a(b),s.y[1].a(c))},
K(a){return this.a.K(a)},
aa(a,b){this.a.aa(0,A.n(this).h("~(1,2)").a(b))},
gL(a){var s=this.a
return s.gL(s)},
ga1(a){var s=this.a
return s.ga1(s)},
gn(a){var s=this.a
return s.gn(s)},
ga9(){return this.a.ga9()},
k(a){return this.a.k(0)},
gaF(){return this.a.gaF()},
bs(a,b,c,d){return this.a.bs(0,A.n(this).A(c).A(d).h("W<1,2>(3,4)").a(b),c,d)},
$iL:1}
A.d_.prototype={}
A.cR.prototype={
gL(a){return this.gn(this)===0},
ga1(a){return this.gn(this)!==0},
B(a,b){var s
for(s=J.aE(A.n(this).h("m<1>").a(b));s.p();)this.m(0,s.gu())},
aZ(a,b,c){var s=A.n(this)
return new A.dL(this,s.A(c).h("1(2)").a(b),s.h("@<1>").A(c).h("dL<1,2>"))},
k(a){return A.yI(this,"{","}")},
aC(a,b){return A.B5(this,b,A.n(this).c)},
X(a,b){var s,r
A.bo(b,"index")
s=this.gC(this)
for(r=b;s.p();){if(r===0)return s.gu();--r}throw A.d(A.pW(b,b-r,this,"index"))},
$iK:1,
$im:1,
$ie1:1}
A.iz.prototype={
cd(a){var s,r,q=this.h_()
for(s=this.gC(this);s.p();){r=s.gu()
if(!a.v(0,r))q.m(0,r)}return q}}
A.fw.prototype={}
A.mo.prototype={
j(a,b){var s,r=this.b
if(r==null)return this.c.j(0,b)
else if(typeof b!="string")return null
else{s=r[b]
return typeof s=="undefined"?this.kI(b):s}},
gn(a){return this.b==null?this.c.a:this.c4().length},
gL(a){return this.gn(0)===0},
ga1(a){return this.gn(0)>0},
ga9(){if(this.b==null){var s=this.c
return new A.aW(s,A.n(s).h("aW<1>"))}return new A.mp(this)},
i(a,b,c){var s,r,q=this
A.r(b)
if(q.b==null)q.c.i(0,b,c)
else if(q.K(b)){s=q.b
s[b]=c
r=q.a
if(r==null?s!=null:r!==s)r[b]=null}else q.lE().i(0,b,c)},
K(a){if(this.b==null)return this.c.K(a)
return Object.prototype.hasOwnProperty.call(this.a,a)},
aa(a,b){var s,r,q,p,o=this
t.lc.a(b)
if(o.b==null)return o.c.aa(0,b)
s=o.c4()
for(r=0;r<s.length;++r){q=s[r]
p=o.b[q]
if(typeof p=="undefined"){p=A.xf(o.a[q])
o.b[q]=p}b.$2(q,p)
if(s!==o.c)throw A.d(A.aB(o))}},
c4(){var s=t.g.a(this.c)
if(s==null)s=this.c=A.a(Object.keys(this.a),t.s)
return s},
lE(){var s,r,q,p,o,n=this
if(n.b==null)return n.c
s=A.t(t.N,t.z)
r=n.c4()
for(q=0;p=r.length,q<p;++q){o=r[q]
s.i(0,o,n.j(0,o))}if(p===0)B.b.m(r,"")
else B.b.O(r)
n.a=n.b=null
return n.c=s},
kI(a){var s
if(!Object.prototype.hasOwnProperty.call(this.a,a))return null
s=A.xf(this.a[a])
return this.b[a]=s}}
A.mp.prototype={
gn(a){return this.a.gn(0)},
X(a,b){var s=this.a
if(s.b==null)s=s.ga9().X(0,b)
else{s=s.c4()
if(!(b>=0&&b<s.length))return A.f(s,b)
s=s[b]}return s},
gC(a){var s=this.a
if(s.b==null){s=s.ga9()
s=s.gC(s)}else{s=s.c4()
s=new J.dI(s,s.length,A.F(s).h("dI<1>"))}return s},
v(a,b){return this.a.K(b)}}
A.wX.prototype={
$0(){var s,r
try{s=new TextDecoder("utf-8",{fatal:true})
return s}catch(r){}return null},
$S:34}
A.wW.prototype={
$0(){var s,r
try{s=new TextDecoder("utf-8",{fatal:false})
return s}catch(r){}return null},
$S:34}
A.je.prototype={
gbe(){return"us-ascii"},
di(a){return B.c6.aE(a)},
a7(a){var s
t.L.a(a)
s=B.c5.aE(a)
return s}}
A.wR.prototype={
aE(a){var s,r,q,p=a.length,o=A.ch(0,null,p),n=new Uint8Array(o)
for(s=~this.a,r=0;r<o;++r){if(!(r<p))return A.f(a,r)
q=a.charCodeAt(r)
if((q&s)!==0)throw A.d(A.dH(a,"string","Contains invalid characters."))
if(!(r<o))return A.f(n,r)
n[r]=q}return n}}
A.nU.prototype={}
A.wQ.prototype={
aE(a){var s,r,q,p,o
t.L.a(a)
s=a.length
r=A.ch(0,null,s)
for(q=~this.b,p=0;p<r;++p){if(!(p<s))return A.f(a,p)
o=a[p]
if((o&q)!==0){if(!this.a)throw A.d(A.ap("Invalid value in input: "+o,null,null))
return this.jN(a,0,r)}}return A.hW(a,0,r)},
jN(a,b,c){var s,r,q,p,o
t.L.a(a)
for(s=~this.b,r=a.length,q=b,p="";q<c;++q){if(!(q<r))return A.f(a,q)
o=a[q]
p+=A.am((o&s)!==0?65533:o)}return p.charCodeAt(0)==0?p:p}}
A.nT.prototype={}
A.fR.prototype={
hX(a3,a4,a5){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e,d,c,b,a,a0="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/",a1="Invalid base64 encoding length ",a2=a3.length
a5=A.ch(a4,a5,a2)
s=$.zw()
for(r=s.length,q=a4,p=q,o=null,n=-1,m=-1,l=0;q<a5;q=k){k=q+1
if(!(q<a2))return A.f(a3,q)
j=a3.charCodeAt(q)
if(j===37){i=k+2
if(i<=a5){if(!(k<a2))return A.f(a3,k)
h=A.y1(a3.charCodeAt(k))
g=k+1
if(!(g<a2))return A.f(a3,g)
f=A.y1(a3.charCodeAt(g))
e=h*16+f-(f&256)
if(e===37)e=-1
k=i}else e=-1}else e=j
if(0<=e&&e<=127){if(!(e>=0&&e<r))return A.f(s,e)
d=s[e]
if(d>=0){if(!(d<64))return A.f(a0,d)
e=a0.charCodeAt(d)
if(e===j)continue
j=e}else{if(d===-1){if(n<0){g=o==null?null:o.a.length
if(g==null)g=0
n=g+(q-p)
m=q}++l
if(j===61)continue}j=e}if(d!==-2){if(o==null){o=new A.aI("")
g=o}else g=o
g.a+=B.a.q(a3,p,q)
c=A.am(j)
g.a+=c
p=k
continue}}throw A.d(A.ap("Invalid base64 data",a3,q))}if(o!=null){a2=B.a.q(a3,p,a5)
a2=o.a+=a2
r=a2.length
if(n>=0)A.Ab(a3,m,a5,n,l,r)
else{b=B.c.bX(r-1,4)+1
if(b===1)throw A.d(A.ap(a1,a3,a5))
while(b<4){a2+="="
o.a=a2;++b}}a2=o.a
return B.a.bf(a3,a4,a5,a2.charCodeAt(0)==0?a2:a2)}a=a5-a4
if(n>=0)A.Ab(a3,m,a5,n,l,a)
else{b=B.c.bX(a,4)
if(b===1)throw A.d(A.ap(a1,a3,a5))
if(b>1)a3=B.a.bf(a3,a5,a5,b===2?"==":"=")}return a3},
cn(a){return this.hX(a,0,null)}}
A.jj.prototype={}
A.nY.prototype={
aE(a){var s,r,q,p=A.ch(0,null,a.length)
if(0===p)return new Uint8Array(0)
s=new A.u8()
r=s.m9(a,0,p)
r.toString
q=s.a
if(q<-1)A.ak(A.ap("Missing padding character",a,p))
if(q>0)A.ak(A.ap("Invalid length, must be multiple of four",a,p))
s.a=-1
return r}}
A.u8.prototype={
m9(a,b,c){var s,r=this,q=r.a
if(q<0){r.a=A.Bi(a,b,c,q)
return null}if(b===c)return new Uint8Array(0)
s=A.Fe(a,b,c,q)
r.a=A.Fg(a,b,c,s,0,r.a)
return s}}
A.o6.prototype={}
A.lW.prototype={
m(a,b){var s,r,q,p,o,n=this
t.fm.a(b)
s=n.b
r=n.c
q=J.aT(b)
if(q.gn(b)>s.length-r){s=n.b
p=q.gn(b)+s.length-1
p|=B.c.bm(p,1)
p|=p>>>2
p|=p>>>4
p|=p>>>8
o=new Uint8Array((((p|p>>>16)>>>0)+1)*2)
s=n.b
B.G.cC(o,0,s.length,s)
n.b=o}s=n.b
r=n.c
B.G.cC(s,r,r+q.gn(b),b)
n.c=n.c+q.gn(b)},
a_(){this.a.$1(B.G.b4(this.b,0,this.c))}}
A.cx.prototype={}
A.jF.prototype={}
A.df.prototype={}
A.ht.prototype={
k(a){var s=A.jN(this.a)
return(this.b!=null?"Converting object to an encodable object failed:":"Converting object did not return an encodable object:")+" "+s}}
A.kc.prototype={
k(a){return"Cyclic error in JSON stringify"}}
A.kb.prototype={
aJ(a,b){var s=A.GJ(a,this.gmb().a)
return s},
bb(a,b){var s=A.Fp(a,this.gmi().b,null)
return s},
gmi(){return B.da},
gmb(){return B.d9}}
A.q2.prototype={}
A.q1.prototype={}
A.vp.prototype={
ii(a){var s,r,q,p,o,n,m=a.length
for(s=this.c,r=0,q=0;q<m;++q){p=a.charCodeAt(q)
if(p>92){if(p>=55296){o=p&64512
if(o===55296){n=q+1
n=!(n<m&&(a.charCodeAt(n)&64512)===56320)}else n=!1
if(!n)if(o===56320){o=q-1
o=!(o>=0&&(a.charCodeAt(o)&64512)===55296)}else o=!1
else o=!0
if(o){if(q>r)s.a+=B.a.q(a,r,q)
r=q+1
o=A.am(92)
s.a+=o
o=A.am(117)
s.a+=o
o=A.am(100)
s.a+=o
o=p>>>8&15
o=A.am(o<10?48+o:87+o)
s.a+=o
o=p>>>4&15
o=A.am(o<10?48+o:87+o)
s.a+=o
o=p&15
o=A.am(o<10?48+o:87+o)
s.a+=o}}continue}if(p<32){if(q>r)s.a+=B.a.q(a,r,q)
r=q+1
o=A.am(92)
s.a+=o
switch(p){case 8:o=A.am(98)
s.a+=o
break
case 9:o=A.am(116)
s.a+=o
break
case 10:o=A.am(110)
s.a+=o
break
case 12:o=A.am(102)
s.a+=o
break
case 13:o=A.am(114)
s.a+=o
break
default:o=A.am(117)
s.a+=o
o=A.am(48)
s.a=(s.a+=o)+o
o=p>>>4&15
o=A.am(o<10?48+o:87+o)
s.a+=o
o=p&15
o=A.am(o<10?48+o:87+o)
s.a+=o
break}}else if(p===34||p===92){if(q>r)s.a+=B.a.q(a,r,q)
r=q+1
o=A.am(92)
s.a+=o
o=A.am(p)
s.a+=o}}if(r===0)s.a+=a
else if(r<m)s.a+=B.a.q(a,r,m)},
e5(a){var s,r,q,p
for(s=this.a,r=s.length,q=0;q<r;++q){p=s[q]
if(a==null?p==null:a===p)throw A.d(new A.kc(a,null))}B.b.m(s,a)},
dM(a){var s,r,q,p,o=this
if(o.ih(a))return
o.e5(a)
try{s=o.b.$1(a)
if(!o.ih(s)){q=A.Aw(a,null,o.gh9())
throw A.d(q)}q=o.a
if(0>=q.length)return A.f(q,-1)
q.pop()}catch(p){r=A.a1(p)
q=A.Aw(a,r,o.gh9())
throw A.d(q)}},
ih(a){var s,r,q=this
if(typeof a=="number"){if(!isFinite(a))return!1
q.c.a+=B.e.k(a)
return!0}else if(a===!0){q.c.a+="true"
return!0}else if(a===!1){q.c.a+="false"
return!0}else if(a==null){q.c.a+="null"
return!0}else if(typeof a=="string"){s=q.c
s.a+='"'
q.ii(a)
s.a+='"'
return!0}else if(t._.b(a)){q.e5(a)
q.no(a)
s=q.a
if(0>=s.length)return A.f(s,-1)
s.pop()
return!0}else if(t.av.b(a)){q.e5(a)
r=q.np(a)
s=q.a
if(0>=s.length)return A.f(s,-1)
s.pop()
return r}else return!1},
no(a){var s,r,q=this.c
q.a+="["
s=J.aT(a)
if(s.ga1(a)){this.dM(s.j(a,0))
for(r=1;r<s.gn(a);++r){q.a+=","
this.dM(s.j(a,r))}}q.a+="]"},
np(a){var s,r,q,p,o,n,m=this,l={}
if(a.gL(a)){m.c.a+="{}"
return!0}s=a.gn(a)*2
r=A.bL(s,null,!1,t.X)
q=l.a=0
l.b=!0
a.aa(0,new A.vq(l,r))
if(!l.b)return!1
p=m.c
p.a+="{"
for(o='"';q<s;q+=2,o=',"'){p.a+=o
m.ii(A.r(r[q]))
p.a+='":'
n=q+1
if(!(n<s))return A.f(r,n)
m.dM(r[n])}p.a+="}"
return!0}}
A.vq.prototype={
$2(a,b){var s,r
if(typeof a!="string")this.a.b=!1
s=this.b
r=this.a
B.b.i(s,r.a++,a)
B.b.i(s,r.a++,b)},
$S:51}
A.vo.prototype={
gh9(){var s=this.c.a
return s.charCodeAt(0)==0?s:s}}
A.kd.prototype={
gbe(){return"iso-8859-1"},
di(a){return B.dg.aE(a)},
a7(a){var s
t.L.a(a)
s=B.df.aE(a)
return s}}
A.qa.prototype={}
A.q9.prototype={}
A.lF.prototype={
gbe(){return"utf-8"},
a7(a){t.L.a(a)
return B.kd.aE(a)},
di(a){return B.cq.aE(a)}}
A.to.prototype={
aE(a){var s,r,q,p=a.length,o=A.ch(0,null,p)
if(o===0)return new Uint8Array(0)
s=new Uint8Array(o*3)
r=new A.wY(s)
if(r.jZ(a,0,o)!==o){q=o-1
if(!(q>=0&&q<p))return A.f(a,q)
r.eE()}return B.G.b4(s,0,r.b)}}
A.wY.prototype={
eE(){var s,r=this,q=r.c,p=r.b,o=r.b=p+1
q.$flags&2&&A.au(q)
s=q.length
if(!(p<s))return A.f(q,p)
q[p]=239
p=r.b=o+1
if(!(o<s))return A.f(q,o)
q[o]=191
r.b=p+1
if(!(p<s))return A.f(q,p)
q[p]=189},
lM(a,b){var s,r,q,p,o,n=this
if((b&64512)===56320){s=65536+((a&1023)<<10)|b&1023
r=n.c
q=n.b
p=n.b=q+1
r.$flags&2&&A.au(r)
o=r.length
if(!(q<o))return A.f(r,q)
r[q]=s>>>18|240
q=n.b=p+1
if(!(p<o))return A.f(r,p)
r[p]=s>>>12&63|128
p=n.b=q+1
if(!(q<o))return A.f(r,q)
r[q]=s>>>6&63|128
n.b=p+1
if(!(p<o))return A.f(r,p)
r[p]=s&63|128
return!0}else{n.eE()
return!1}},
jZ(a,b,c){var s,r,q,p,o,n,m,l,k=this
if(b!==c){s=c-1
if(!(s>=0&&s<a.length))return A.f(a,s)
s=(a.charCodeAt(s)&64512)===55296}else s=!1
if(s)--c
for(s=k.c,r=s.$flags|0,q=s.length,p=a.length,o=b;o<c;++o){if(!(o<p))return A.f(a,o)
n=a.charCodeAt(o)
if(n<=127){m=k.b
if(m>=q)break
k.b=m+1
r&2&&A.au(s)
s[m]=n}else{m=n&64512
if(m===55296){if(k.b+4>q)break
m=o+1
if(!(m<p))return A.f(a,m)
if(k.lM(n,a.charCodeAt(m)))o=m}else if(m===56320){if(k.b+3>q)break
k.eE()}else if(n<=2047){m=k.b
l=m+1
if(l>=q)break
k.b=l
r&2&&A.au(s)
if(!(m<q))return A.f(s,m)
s[m]=n>>>6|192
k.b=l+1
s[l]=n&63|128}else{m=k.b
if(m+2>=q)break
l=k.b=m+1
r&2&&A.au(s)
if(!(m<q))return A.f(s,m)
s[m]=n>>>12|224
m=k.b=l+1
if(!(l<q))return A.f(s,l)
s[l]=n>>>6&63|128
k.b=m+1
if(!(m<q))return A.f(s,m)
s[m]=n&63|128}}}return o}}
A.tn.prototype={
aE(a){return new A.wV(this.a).jM(t.L.a(a),0,null,!0)}}
A.wV.prototype={
jM(a,b,c,d){var s,r,q,p,o,n,m,l=this
t.L.a(a)
s=A.ch(b,c,J.b4(a))
if(b===s)return""
if(a instanceof Uint8Array){r=a
q=r
p=0}else{q=A.FY(a,b,s)
s-=b
p=b
b=0}if(s-b>=15){o=l.a
n=A.FX(o,q,b,s)
if(n!=null){if(!o)return n
if(n.indexOf("\ufffd")<0)return n}}n=l.ef(q,b,s,!0)
o=l.b
if((o&1)!==0){m=A.FZ(o)
l.b=0
throw A.d(A.ap(m,a,p+l.c))}return n},
ef(a,b,c,d){var s,r,q=this
if(c-b>1000){s=B.c.ag(b+c,2)
r=q.ef(a,b,s,!1)
if((q.b&1)!==0)return r
return r+q.ef(a,s,c,d)}return q.ma(a,b,c,d)},
ma(a,b,a0,a1){var s,r,q,p,o,n,m,l,k=this,j="AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFFFFFFFFFFFFFFFFGGGGGGGGGGGGGGGGHHHHHHHHHHHHHHHHHHHHHHHHHHHIHHHJEEBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBKCCCCCCCCCCCCDCLONNNMEEEEEEEEEEE",i=" \x000:XECCCCCN:lDb \x000:XECCCCCNvlDb \x000:XECCCCCN:lDb AAAAA\x00\x00\x00\x00\x00AAAAA00000AAAAA:::::AAAAAGG000AAAAA00KKKAAAAAG::::AAAAA:IIIIAAAAA000\x800AAAAA\x00\x00\x00\x00 AAAAA",h=65533,g=k.b,f=k.c,e=new A.aI(""),d=b+1,c=a.length
if(!(b>=0&&b<c))return A.f(a,b)
s=a[b]
A:for(r=k.a;;){for(;;d=o){if(!(s>=0&&s<256))return A.f(j,s)
q=j.charCodeAt(s)&31
f=g<=32?s&61694>>>q:(s&63|f<<6)>>>0
p=g+q
if(!(p>=0&&p<144))return A.f(i,p)
g=i.charCodeAt(p)
if(g===0){p=A.am(f)
e.a+=p
if(d===a0)break A
break}else if((g&1)!==0){if(r)switch(g){case 69:case 67:p=A.am(h)
e.a+=p
break
case 65:p=A.am(h)
e.a+=p;--d
break
default:p=A.am(h)
e.a=(e.a+=p)+p
break}else{k.b=g
k.c=d-1
return""}g=0}if(d===a0)break A
o=d+1
if(!(d>=0&&d<c))return A.f(a,d)
s=a[d]}o=d+1
if(!(d>=0&&d<c))return A.f(a,d)
s=a[d]
if(s<128){for(;;){if(!(o<a0)){n=a0
break}m=o+1
if(!(o>=0&&o<c))return A.f(a,o)
s=a[o]
if(s>=128){n=m-1
o=m
break}o=m}if(n-d<20)for(l=d;l<n;++l){if(!(l<c))return A.f(a,l)
p=A.am(a[l])
e.a+=p}else{p=A.hW(a,d,n)
e.a+=p}if(n===a0)break A
d=o}else d=o}if(a1&&g>32)if(r){c=A.am(h)
e.a+=c}else{k.b=77
k.c=a0
return""}k.b=g
k.c=f
c=e.a
return c.charCodeAt(0)==0?c:c}}
A.b6.prototype={
cd(a){return A.Ap(this.b-a.b,this.a-a.a)},
N(a,b){if(b==null)return!1
return b instanceof A.b6&&this.a===b.a&&this.b===b.b&&this.c===b.c},
gI(a){return A.cL(this.a,this.b,B.d,B.d,B.d,B.d,B.d,B.d,B.d,B.d)},
P(a,b){var s
t.cs.a(b)
s=B.c.P(this.a,b.a)
if(s!==0)return s
return B.c.P(this.b,b.b)},
k(a){var s=this,r=A.Am(A.kE(s)),q=A.cz(A.AR(s)),p=A.cz(A.AN(s)),o=A.cz(A.AO(s)),n=A.cz(A.AQ(s)),m=A.cz(A.AS(s)),l=A.oI(A.AP(s)),k=s.b,j=k===0?"":A.oI(k)
k=r+"-"+q
if(s.c)return k+"-"+p+" "+o+":"+n+":"+m+"."+l+j+"Z"
else return k+"-"+p+" "+o+":"+n+":"+m+"."+l+j},
ng(){var s=this,r=A.kE(s)>=-9999&&A.kE(s)<=9999?A.Am(A.kE(s)):A.DH(A.kE(s)),q=A.cz(A.AR(s)),p=A.cz(A.AN(s)),o=A.cz(A.AO(s)),n=A.cz(A.AQ(s)),m=A.cz(A.AS(s)),l=A.oI(A.AP(s)),k=s.b,j=k===0?"":A.oI(k)
k=r+"-"+q
if(s.c)return k+"-"+p+"T"+o+":"+n+":"+m+"."+l+j+"Z"
else return k+"-"+p+"T"+o+":"+n+":"+m+"."+l+j},
$iax:1}
A.ca.prototype={
N(a,b){if(b==null)return!1
return b instanceof A.ca&&this.a===b.a},
gI(a){return B.c.gI(this.a)},
P(a,b){return B.c.P(this.a,t.jS.a(b).a)},
k(a){var s,r,q,p,o,n=this.a,m=B.c.ag(n,36e8),l=n%36e8
if(n<0){m=0-m
n=0-l
s="-"}else{n=l
s=""}r=B.c.ag(n,6e7)
n%=6e7
q=r<10?"0":""
p=B.c.ag(n,1e6)
o=p<10?"0":""
return s+m+":"+q+r+":"+o+p+"."+B.a.dw(B.c.k(n%1e6),6,"0")},
$iax:1}
A.us.prototype={
k(a){return this.E()}}
A.ad.prototype={
gbh(){return A.EB(this)}}
A.jf.prototype={
k(a){var s=this.a
if(s!=null)return"Assertion failed: "+A.jN(s)
return"Assertion failed"}}
A.cY.prototype={}
A.bI.prototype={
gei(){return"Invalid argument"+(!this.a?"(s)":"")},
geh(){return""},
k(a){var s=this,r=s.c,q=r==null?"":" ("+r+")",p=s.d,o=p==null?"":": "+A.w(p),n=s.gei()+q+o
if(!s.a)return n
return n+s.geh()+": "+A.jN(s.geW())},
geW(){return this.b}}
A.f_.prototype={
geW(){return A.BV(this.b)},
gei(){return"RangeError"},
geh(){var s,r=this.e,q=this.f
if(r==null)s=q!=null?": Not less than or equal to "+A.w(q):""
else if(q==null)s=": Not greater than or equal to "+A.w(r)
else if(q>r)s=": Not in inclusive range "+A.w(r)+".."+A.w(q)
else s=q<r?": Valid value range is empty":": Only valid value is "+A.w(r)
return s}}
A.k2.prototype={
geW(){return A.bb(this.b)},
gei(){return"RangeError"},
geh(){if(A.bb(this.b)<0)return": index must not be negative"
var s=this.f
if(s===0)return": no indices are valid"
return": index should be less than "+s},
gn(a){return this.f}}
A.hY.prototype={
k(a){return"Unsupported operation: "+this.a}}
A.lA.prototype={
k(a){var s=this.a
return s!=null?"UnimplementedError: "+s:"UnimplementedError"}}
A.ck.prototype={
k(a){return"Bad state: "+this.a}}
A.jy.prototype={
k(a){var s=this.a
if(s==null)return"Concurrent modification during iteration."
return"Concurrent modification during iteration: "+A.jN(s)+"."}}
A.kw.prototype={
k(a){return"Out of Memory"},
gbh(){return null},
$iad:1}
A.hS.prototype={
k(a){return"Stack Overflow"},
gbh(){return null},
$iad:1}
A.dw.prototype={
k(a){return"Exception: "+A.w(this.a)},
$iaj:1}
A.bn.prototype={
k(a){var s,r,q,p,o,n,m,l,k,j,i,h=this.a,g=""!==h?"FormatException: "+h:"FormatException",f=this.c,e=this.b
if(typeof e=="string"){if(f!=null)s=f<0||f>e.length
else s=!1
if(s)f=null
if(f==null){if(e.length>78)e=B.a.q(e,0,75)+"..."
return g+"\n"+e}for(r=e.length,q=1,p=0,o=!1,n=0;n<f;++n){if(!(n<r))return A.f(e,n)
m=e.charCodeAt(n)
if(m===10){if(p!==n||!o)++q
p=n+1
o=!1}else if(m===13){++q
p=n+1
o=!0}}g=q>1?g+(" (at line "+q+", character "+(f-p+1)+")\n"):g+(" (at character "+(f+1)+")\n")
for(n=f;n<r;++n){if(!(n>=0))return A.f(e,n)
m=e.charCodeAt(n)
if(m===10||m===13){r=n
break}}l=""
if(r-p>78){k="..."
if(f-p<75){j=p+75
i=p}else{if(r-f<75){i=r-75
j=r
k=""}else{i=f-36
j=f+36}l="..."}}else{j=r
i=p
k=""}return g+l+B.a.q(e,i,j)+k+"\n"+B.a.aB(" ",f-i+l.length)+"^\n"}else return f!=null?g+(" (at offset "+A.w(f)+")"):g},
$iaj:1,
geZ(){return this.a},
gcH(){return this.b},
ga6(){return this.c}}
A.m.prototype={
cc(a,b){return A.Ah(this,A.n(this).h("m.E"),b)},
aZ(a,b,c){var s=A.n(this)
return A.qs(this,s.A(c).h("1(m.E)").a(b),s.h("m.E"),c)},
dJ(a,b){var s=A.n(this)
return new A.a3(this,s.h("y(m.E)").a(b),s.h("a3<m.E>"))},
v(a,b){var s
for(s=this.gC(this);s.p();)if(J.a8(s.gu(),b))return!0
return!1},
aA(a,b){var s,r,q=this.gC(this)
if(!q.p())return""
s=J.aF(q.gu())
if(!q.p())return s
if(b.length===0){r=s
do r+=J.aF(q.gu())
while(q.p())}else{r=s
do r=r+b+J.aF(q.gu())
while(q.p())}return r.charCodeAt(0)==0?r:r},
b0(a,b){var s=A.n(this).h("m.E")
if(b)s=A.x(this,s)
else{s=A.x(this,s)
s.$flags=1
s=s}return s},
dG(a){return this.b0(0,!0)},
gn(a){var s,r=this.gC(this)
for(s=0;r.p();)++s
return s},
gL(a){return!this.gC(this).p()},
ga1(a){return!this.gL(this)},
aC(a,b){return A.B5(this,b,A.n(this).h("m.E"))},
X(a,b){var s,r
A.bo(b,"index")
s=this.gC(this)
for(r=b;s.p();){if(r===0)return s.gu();--r}throw A.d(A.pW(b,b-r,this,"index"))},
k(a){return A.Ef(this,"(",")")}}
A.W.prototype={
k(a){return"MapEntry("+A.w(this.a)+": "+A.w(this.b)+")"}}
A.aa.prototype={
gI(a){return A.u.prototype.gI.call(this,0)},
k(a){return"null"}}
A.u.prototype={$iu:1,
N(a,b){return this===b},
gI(a){return A.b0(this)},
k(a){return"Instance of '"+A.kF(this)+"'"},
ga2(a){return A.bH(this)},
toString(){return this.k(this)}}
A.mJ.prototype={
k(a){return""},
$iba:1}
A.aI.prototype={
gn(a){return this.a.length},
k(a){var s=this.a
return s.charCodeAt(0)==0?s:s},
$iEZ:1}
A.tm.prototype={
$2(a,b){var s,r,q,p
t.f.a(a)
A.r(b)
s=B.a.aU(b,"=")
if(s===-1){if(b!=="")a.i(0,A.d6(b,0,b.length,this.a,!0),"")}else if(s!==0){r=B.a.q(b,0,s)
q=B.a.S(b,s+1)
p=this.a
a.i(0,A.d6(r,0,r.length,p,!0),A.d6(q,0,q.length,p,!0))}return a},
$S:147}
A.tl.prototype={
$2(a,b){throw A.d(A.ap("Illegal IPv6 address, "+a,this.a,b))},
$S:141}
A.iM.prototype={
ghl(){var s,r,q,p,o=this,n=o.w
if(n===$){s=o.a
r=s.length!==0?s+":":""
q=o.c
p=q==null
if(!p||s==="file"){s=r+"//"
r=o.b
if(r.length!==0)s=s+r+"@"
if(!p)s+=q
r=o.d
if(r!=null)s=s+":"+A.w(r)}else s=r
s+=o.e
r=o.f
if(r!=null)s=s+"?"+r
r=o.r
if(r!=null)s=s+"#"+r
n=o.w=s.charCodeAt(0)==0?s:s}return n},
gn_(){var s,r,q,p=this,o=p.x
if(o===$){s=p.e
r=s.length
if(r!==0){if(0>=r)return A.f(s,0)
r=s.charCodeAt(0)===47}else r=!1
if(r)s=B.a.S(s,1)
q=s.length===0?B.P:A.al(new A.E(A.a(s.split("/"),t.s),t.f5.a(A.Hc()),t.iZ),t.N)
p.x!==$&&A.fI()
o=p.x=q}return o},
gI(a){var s,r=this,q=r.y
if(q===$){s=B.a.gI(r.ghl())
r.y!==$&&A.fI()
r.y=s
q=s}return q},
gdA(){var s,r=this,q=r.z
if(q===$){s=r.f
s=A.Bf(s==null?"":s)
r.z!==$&&A.fI()
q=r.z=new A.d_(s,t.ph)}return q},
gdB(){var s,r,q=this,p=q.Q
if(p===$){s=q.f
r=A.FR(s==null?"":s)
q.Q!==$&&A.fI()
q.Q=r
p=r}return p},
gfg(){return this.b},
gbr(){var s=this.c
if(s==null)return""
if(B.a.M(s,"[")&&!B.a.V(s,"v",1))return B.a.q(s,1,s.length-1)
return s},
gco(){var s=this.d
return s==null?A.BD(this.a):s},
gbu(){var s=this.f
return s==null?"":s},
gdl(){var s=this.r
return s==null?"":s},
mF(a){var s=this.a
if(a.length!==s.length)return!1
return A.G9(a,s,0)>=0},
i2(a){var s,r,q,p,o,n,m,l=this
a=A.z6(a,0,a.length)
s=a==="file"
r=l.b
q=l.d
if(a!==l.a)q=A.wT(q,a)
p=l.c
if(!(p!=null))p=r.length!==0||q!=null||s?"":null
o=l.e
if(!s)n=p!=null&&o.length!==0
else n=!0
if(n&&!B.a.M(o,"/"))o="/"+o
m=o
return A.iN(a,r,p,q,m,l.f,l.r)},
fX(a,b){var s,r,q,p,o,n,m,l,k
for(s=0,r=0;B.a.V(b,"../",r);){r+=3;++s}q=B.a.eX(a,"/")
p=a.length
for(;;){if(!(q>0&&s>0))break
o=B.a.dt(a,"/",q-1)
if(o<0)break
n=q-o
m=n!==2
l=!1
if(!m||n===3){k=o+1
if(!(k<p))return A.f(a,k)
if(a.charCodeAt(k)===46)if(m){m=o+2
if(!(m<p))return A.f(a,m)
m=a.charCodeAt(m)===46}else m=!0
else m=l}else m=l
if(m)break;--s
q=o}return B.a.bf(a,q+1,null,B.a.S(b,r-3*s))},
i6(a){return this.cr(A.bN(a))},
cr(a){var s,r,q,p,o,n,m,l,k,j,i,h=this
if(a.gam().length!==0)return a
else{s=h.a
if(a.geS()){r=a.i2(s)
return r}else{q=h.b
p=h.c
o=h.d
n=h.e
if(a.ghK())m=a.gdm()?a.gbu():h.f
else{l=A.FW(h,n)
if(l>0){k=B.a.q(n,0,l)
n=a.geR()?k+A.ek(a.gab()):k+A.ek(h.fX(B.a.S(n,k.length),a.gab()))}else if(a.geR())n=A.ek(a.gab())
else if(n.length===0)if(p==null)n=s.length===0?a.gab():A.ek(a.gab())
else n=A.ek("/"+a.gab())
else{j=h.fX(n,a.gab())
r=s.length===0
if(!r||p!=null||B.a.M(n,"/"))n=A.ek(j)
else n=A.z8(j,!r||p!=null)}m=a.gdm()?a.gbu():null}}}i=a.geT()?a.gdl():null
return A.iN(s,q,p,o,n,m,i)},
geS(){return this.c!=null},
gdm(){return this.f!=null},
geT(){return this.r!=null},
ghK(){return this.e.length===0},
geR(){return B.a.M(this.e,"/")},
fc(){var s,r=this,q=r.a
if(q!==""&&q!=="file")throw A.d(A.ao("Cannot extract a file path from a "+q+" URI"))
q=r.f
if((q==null?"":q)!=="")throw A.d(A.ao(u.y))
q=r.r
if((q==null?"":q)!=="")throw A.d(A.ao(u.l))
if(r.c!=null&&r.gbr()!=="")A.ak(A.ao(u.j))
s=r.gn_()
A.FP(s,!1)
q=A.yU(B.a.M(r.e,"/")?"/":"",s,"/")
q=q.charCodeAt(0)==0?q:q
return q},
k(a){return this.ghl()},
N(a,b){var s,r,q,p=this
if(b==null)return!1
if(p===b)return!0
s=!1
if(t.R.b(b))if(p.a===b.gam())if(p.c!=null===b.geS())if(p.b===b.gfg())if(p.gbr()===b.gbr())if(p.gco()===b.gco())if(p.e===b.gab()){r=p.f
q=r==null
if(!q===b.gdm()){if(q)r=""
if(r===b.gbu()){r=p.r
q=r==null
if(!q===b.geT()){s=q?"":r
s=s===b.gdl()}}}}return s},
$ilC:1,
gam(){return this.a},
gab(){return this.e}}
A.wU.prototype={
$3(a,b,c){var s,r,q,p
if(a===c)return
s=this.a
r=this.b
if(b<0){q=A.d6(s,a,c,r,!0)
p=""}else{q=A.d6(s,a,b,r,!0)
p=A.d6(s,b+1,c,r,!0)}J.fJ(this.c.dz(q,A.Hd()),p)},
$S:138}
A.tk.prototype={
gig(){var s,r,q,p,o=this,n=null,m=o.c
if(m==null){m=o.b
if(0>=m.length)return A.f(m,0)
s=o.a
m=m[0]+1
r=B.a.aV(s,"?",m)
q=s.length
if(r>=0){p=A.iO(s,r+1,q,256,!1,!1)
q=r}else p=n
m=o.c=new A.m_("data","",n,n,A.iO(s,m,q,128,!1,!1),p,n)}return m},
k(a){var s,r=this.b
if(0>=r.length)return A.f(r,0)
s=this.a
return r[0]===-1?"data:"+s:s}}
A.bR.prototype={
geS(){return this.c>0},
geU(){return this.c>0&&this.d+1<this.e},
gdm(){return this.f<this.r},
geT(){return this.r<this.a.length},
geR(){return B.a.V(this.a,"/",this.e)},
ghK(){return this.e===this.f},
gam(){var s=this.w
return s==null?this.w=this.jH():s},
jH(){var s,r=this,q=r.b
if(q<=0)return""
s=q===4
if(s&&B.a.M(r.a,"http"))return"http"
if(q===5&&B.a.M(r.a,"https"))return"https"
if(s&&B.a.M(r.a,"file"))return"file"
if(q===7&&B.a.M(r.a,"package"))return"package"
return B.a.q(r.a,0,q)},
gfg(){var s=this.c,r=this.b+3
return s>r?B.a.q(this.a,r,s-1):""},
gbr(){var s=this.c
return s>0?B.a.q(this.a,s,this.d):""},
gco(){var s,r=this
if(r.geU())return A.CE(B.a.q(r.a,r.d+1,r.e),null)
s=r.b
if(s===4&&B.a.M(r.a,"http"))return 80
if(s===5&&B.a.M(r.a,"https"))return 443
return 0},
gab(){return B.a.q(this.a,this.e,this.f)},
gbu(){var s=this.f,r=this.r
return s<r?B.a.q(this.a,s+1,r):""},
gdl(){var s=this.r,r=this.a
return s<r.length?B.a.S(r,s+1):""},
gdA(){if(this.f>=this.r)return B.x
return new A.d_(A.Bf(this.gbu()),t.ph)},
gdB(){if(this.f>=this.r)return B.b1
var s=A.BO(this.gbu())
s.ic(A.Ct())
return A.yy(s,t.N,t.h)},
fS(a){var s=this.d+1
return s+a.length===this.e&&B.a.V(this.a,a,s)},
n6(){var s=this,r=s.r,q=s.a
if(r>=q.length)return s
return new A.bR(B.a.q(q,0,r),s.b,s.c,s.d,s.e,s.f,r,s.w)},
i2(a){var s,r,q,p,o,n,m,l,k,j,i,h=this,g=null
a=A.z6(a,0,a.length)
s=!(h.b===a.length&&B.a.M(h.a,a))
r=a==="file"
q=h.c
p=q>0?B.a.q(h.a,h.b+3,q):""
o=h.geU()?h.gco():g
if(s)o=A.wT(o,a)
q=h.c
if(q>0)n=B.a.q(h.a,q,h.d)
else n=p.length!==0||o!=null||r?"":g
q=h.a
m=h.f
l=B.a.q(q,h.e,m)
if(!r)k=n!=null&&l.length!==0
else k=!0
if(k&&!B.a.M(l,"/"))l="/"+l
k=h.r
j=m<k?B.a.q(q,m+1,k):g
m=h.r
i=m<q.length?B.a.S(q,m+1):g
return A.iN(a,p,n,o,l,j,i)},
i6(a){return this.cr(A.bN(a))},
cr(a){if(a instanceof A.bR)return this.ll(this,a)
return this.hn().cr(a)},
ll(a,b){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e,d,c=b.b
if(c>0)return b
s=b.c
if(s>0){r=a.b
if(r<=0)return b
q=r===4
if(q&&B.a.M(a.a,"file"))p=b.e!==b.f
else if(q&&B.a.M(a.a,"http"))p=!b.fS("80")
else p=!(r===5&&B.a.M(a.a,"https"))||!b.fS("443")
if(p){o=r+1
return new A.bR(B.a.q(a.a,0,o)+B.a.S(b.a,c+1),r,s+o,b.d+o,b.e+o,b.f+o,b.r+o,a.w)}else return this.hn().cr(b)}n=b.e
c=b.f
if(n===c){s=b.r
if(c<s){r=a.f
o=r-c
return new A.bR(B.a.q(a.a,0,r)+B.a.S(b.a,c),a.b,a.c,a.d,a.e,c+o,s+o,a.w)}c=b.a
if(s<c.length){r=a.r
return new A.bR(B.a.q(a.a,0,r)+B.a.S(c,s),a.b,a.c,a.d,a.e,a.f,s+(r-s),a.w)}return a.n6()}s=b.a
if(B.a.V(s,"/",n)){m=a.e
l=A.Bw(this)
k=l>0?l:m
o=k-n
return new A.bR(B.a.q(a.a,0,k)+B.a.S(s,n),a.b,a.c,a.d,m,c+o,b.r+o,a.w)}j=a.e
i=a.f
if(j===i&&a.c>0){while(B.a.V(s,"../",n))n+=3
o=j-n+1
return new A.bR(B.a.q(a.a,0,j)+"/"+B.a.S(s,n),a.b,a.c,a.d,j,c+o,b.r+o,a.w)}h=a.a
l=A.Bw(this)
if(l>=0)g=l
else for(g=j;B.a.V(h,"../",g);)g+=3
f=0
for(;;){e=n+3
if(!(e<=c&&B.a.V(s,"../",n)))break;++f
n=e}for(r=h.length,d="";i>g;){--i
if(!(i>=0&&i<r))return A.f(h,i)
if(h.charCodeAt(i)===47){if(f===0){d="/"
break}--f
d="/"}}if(i===g&&a.b<=0&&!B.a.V(h,"/",j)){n-=f*3
d=""}o=i-n+d.length
return new A.bR(B.a.q(h,0,i)+d+B.a.S(s,n),a.b,a.c,a.d,j,c+o,b.r+o,a.w)},
fc(){var s,r=this,q=r.b
if(q>=0){s=!(q===4&&B.a.M(r.a,"file"))
q=s}else q=!1
if(q)throw A.d(A.ao("Cannot extract a file path from a "+r.gam()+" URI"))
q=r.f
s=r.a
if(q<s.length){if(q<r.r)throw A.d(A.ao(u.y))
throw A.d(A.ao(u.l))}if(r.c<r.d)A.ak(A.ao(u.j))
q=B.a.q(s,r.e,q)
return q},
gI(a){var s=this.x
return s==null?this.x=B.a.gI(this.a):s},
N(a,b){if(b==null)return!1
if(this===b)return!0
return t.R.b(b)&&this.a===b.k(0)},
hn(){var s=this,r=null,q=s.gam(),p=s.gfg(),o=s.c>0?s.gbr():r,n=s.geU()?s.gco():r,m=s.a,l=s.f,k=B.a.q(m,s.e,l),j=s.r
l=l<j?s.gbu():r
return A.iN(q,p,o,n,k,l,j<m.length?s.gdl():r)},
k(a){return this.a},
$ilC:1}
A.m_.prototype={}
A.jQ.prototype={
k(a){return"Expando:"+this.b}}
A.kt.prototype={
k(a){return"Promise was rejected with a value of `"+(this.a?"undefined":"null")+"`."},
$iaj:1}
A.y6.prototype={
$1(a){var s,r,q,p
if(A.C9(a))return a
s=this.a
if(s.K(a))return s.j(0,a)
if(t.av.b(a)){r={}
s.i(0,a,r)
for(s=a.ga9(),s=s.gC(s);s.p();){q=s.gu()
r[q]=this.$1(a.j(0,q))}return r}else if(t.e7.b(a)){p=[]
s.i(0,a,p)
B.b.B(p,J.aU(a,this,t.z))
return p}else return a},
$S:39}
A.yc.prototype={
$1(a){return this.a.ba(this.b.h("0/?").a(a))},
$S:25}
A.yd.prototype={
$1(a){if(a==null)return this.a.bI(new A.kt(a===undefined))
return this.a.bI(a)},
$S:25}
A.xV.prototype={
$1(a){var s,r,q,p,o,n,m,l,k,j,i
if(A.C8(a))return a
s=this.a
a.toString
if(s.K(a))return s.j(0,a)
if(a instanceof Date)return new A.b6(A.DI(a.getTime(),0,!0),0,!0)
if(a instanceof RegExp)throw A.d(A.ai("structured clone of RegExp",null))
if(a instanceof Promise)return A.nk(a,t.X)
r=Object.getPrototypeOf(a)
if(r===Object.prototype||r===null){q=t.X
p=A.t(q,q)
s.i(0,a,p)
o=Object.keys(a)
n=[]
for(s=J.bl(o),q=s.gC(o);q.p();)n.push(A.xU(q.gu()))
for(m=0;m<s.gn(o);++m){l=s.j(o,m)
if(!(m<n.length))return A.f(n,m)
k=n[m]
if(l!=null)p.i(0,k,this.$1(a[l]))}return p}if(a instanceof Array){j=a
p=[]
s.i(0,a,p)
i=A.bb(a.length)
for(s=J.aT(j),m=0;m<i;++m)p.push(this.$1(s.j(j,m)))
return p}return a},
$S:39}
A.fW.prototype={
l(a){var s=this
A.b5(a)
return new A.kW(new A.od(s.d,null,s.f,s.r,s.w,null,!0,s.x),null)}}
A.h_.prototype={
l(a){return new A.jw(this.d,B.cz,this.e,null)}}
A.fM.prototype={
l(a){var s=this
A.b5(a)
return new A.kY(new A.or(s.d,s.e,s.f,"Cancel",s.w,s.x,!0,null),null)}}
A.jb.prototype={
l(a){return new A.ft(B.y,3,12,20,null)}}
A.ft.prototype={
U(){return new A.iG()}}
A.iG.prototype={
aW(){var s,r
this.bi()
s=$.bD
if(s==null){s=$.bD=new A.cX(A.a([],t.I),A.a([],t.u),B.y)
r=s}else r=s
s.c=this.a.d
B.b.m(r.b,t.M.a(this.gh4()))},
aq(){var s=$.bD
if(s==null)s=$.bD=new A.cX(A.a([],t.I),A.a([],t.u),B.y)
B.b.J(s.b,t.M.a(this.gh4()))
this.by()},
kx(){this.t(new A.wL())},
l(a){var s,r,q,p=$.bD
p=A.al((p==null?$.bD=new A.cX(A.a([],t.I),A.a([],t.u),B.y):p).a,t.dO)
s=A.e4(p,0,A.fF(this.a.e,"count",t.S),A.F(p).c)
p=s.$ti
r=p.h("E<z.E,e6>")
q=A.x(new A.E(s,p.h("e6(z.E)").a(new A.wM()),r),r.h("z.E"))
A.b5(a)
p=this.a
return new A.l9(new A.t9(p.d,p.e,p.f,p.r,q),null)}}
A.wL.prototype={
$0(){},
$S:0}
A.wM.prototype={
$1(a){t.dO.a(a)
return new A.e6(a.b,a.c,a.d,a.e,a.y,a.f,!0,a.w,a.x,a.a)},
$S:128}
A.cn.prototype={}
A.cX.prototype={
h0(){var s,r,q
for(s=this.b,r=s.length,q=0;q<s.length;s.length===r||(0,A.I)(s),++q)s[q].$0()},
ey(a){B.b.cg(this.a,0,a)
this.h0()
return a.a}}
A.fP.prototype={
l(a){return new A.ev(this.d,null,this.e,B.cM,250,null)}}
A.aY.prototype={
l(a){var s=this,r=null
A.b5(a)
return new A.l5(new A.rY(s.d,B.A,s.f,s.r,!1,s.x,s.y,r,r,!1,r,r,r,r,r),r)}}
A.cv.prototype={
l(a){var s=this,r=null
A.b5(a)
return new A.f9(new A.fV(s.d,r,r,r,s.w,r,s.y,s.z,s.Q,!1,s.at,r,r,r,!1),r)}}
A.j3.prototype={
l(a){var s=null,r=$.zF+1
$.zF=r
A.b5(a)
return new A.kX(new A.oe("arcane-checkbox-"+r,this.e,this.f,s,B.A,B.aI,!1,this.as,s,s),s)}}
A.j5.prototype={
l(a){var s=null,r=this.d,q=A.F(r),p=q.h("E<1,cg>"),o=A.x(new A.E(r,q.h("cg(1)").a(new A.nM()),p),p.h("z.E"))
A.b5(a)
return new A.l1(new A.qz(o,this.e,s,B.A,!1,!1,s,s,s,s,!1,this.ax),s)}}
A.nM.prototype={
$1(a){t.oZ.a(a)
return new A.cg(a.a,a.b,!1)},
$S:125}
A.lu.prototype={
l(a){var s=this,r=null
A.b5(a)
return new A.l7(new A.t4(s.d,s.e,s.y,r,r,B.A,s.r,!1,!1,s.go,s.ch,s.ay,s.ax,r,r,s.CW,r,r,s.db,r,r,r,r,r),r)}}
A.j9.prototype={
l(a){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e=this,d=null
switch(e.r.a){case 0:s=t.N
s=A.j(["height","36px","padding","0 12px","font-size","14px"],s,s)
break
case 1:s=t.N
s=A.j(["height","40px","padding","8px 12px","font-size","16px"],s,s)
break
case 2:s=t.N
s=A.j(["height","44px","padding","0 16px","font-size","16px"],s,s)
break
default:s=d}r=t.N
q=A.t(r,r)
p=e.w
if(p)q.i(0,"disabled","true")
s=A.ce(s,r,r)
s.i(0,"padding-right","36px")
s.i(0,"font-family","inherit")
s.i(0,"background-color","var(--background)")
s.i(0,"border","1px solid var(--input)")
s.i(0,"border-radius","0.375rem")
s.i(0,"color","var(--foreground)")
s.i(0,"transition","color 150ms ease, border-color 150ms ease, box-shadow 150ms ease")
s.i(0,"cursor","pointer")
s.i(0,"appearance","none")
s.i(0,"background-image","url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%2371717A' d='M2.5 4.5L6 8l3.5-3.5'/%3E%3C/svg%3E\")")
s.i(0,"background-repeat","no-repeat")
s.i(0,"background-position","right 12px center")
if(p)s.i(0,"opacity","0.5")
if(p)s.i(0,"cursor","not-allowed")
s=A.B(s)
p=A.t(r,t.v)
if(e.at!=null)p.i(0,"change",new A.nO(e))
o=t.i
n=A.a([],o)
for(m=e.d,l=m.length,k=e.e,j=0;j<m.length;m.length===l||(0,A.I)(m),++j){i=m[j]
h=A.t(r,r)
g=i.b
h.i(0,"value",g)
if(k===g)h.i(0,"selected","true")
n.push(new A.X("option",d,d,d,h,d,A.a([new A.k(i.a,d)],o),d))}f=new A.X("select",e.z,"arcane-select",s,q,p,n,d)
s=e.Q
if(s!=null){r=A.t(r,r)
r.i(0,"display","flex")
r.i(0,"flex-direction","column")
r.i(0,"gap","0.25rem")
r=A.B(r)
q=A.a([],o)
s=A.a([new A.k(s,d)],o)
q.push(new A.X("label",d,d,B.lL,d,d,s,d))
q.push(f)
return new A.c(d,"arcane-select-wrapper",r,d,d,q,d)}return f}}
A.nO.prototype={
$1(a){var s=A.a7(A.p(a).target).gfh(),r=this.a.at;(r==null?t.eF.a(r):r).$1(s)},
$S:4}
A.ah.prototype={}
A.jd.prototype={
l(a){var s=this,r=$.A8+1
$.A8=r
A.b5(a)
return new A.la(new A.tb("arcane-toggle-switch-"+r,s.e,s.f,s.r,B.A,B.aI,s.y,!1,null,null),null)}}
A.dF.prototype={}
A.j2.prototype={
l(a){var s,r,q,p,o
for(s=this.d,r=0;r<1;++r);q=A.F(s)
p=q.h("E<1,dE>")
o=A.x(new A.E(s,q.h("dE(1)").a(new A.nK()),p),p.h("z.E"))
A.b5(a)
return new A.kV(new A.nt(o,A.cJ(t.S),!1),null)}}
A.nK.prototype={
$1(a){t.eS.a(a)
return new A.dE(a.a,"",a.c)},
$S:113}
A.jw.prototype={
l(a){var s,r,q=null
A.b5(a)
s=this.w
r=t.N
r=A.t(r,r)
r.i(0,"display","flex")
r.i(0,"flex-direction","column")
r.i(0,"justify-content",B.dB.gaT())
r.i(0,"align-items",this.f.gaT())
r.i(0,"height","100%")
if(s>0)r.i(0,"gap",""+s+"px")
return new A.c(q,"arcane-column",A.B(r),q,q,this.d,q)}}
A.j6.prototype={
l(a){var s,r,q,p,o,n,m=null
A.b5(a)
s=this.r
r=this.w
q=t.i
p=A.a([],q)
o=t.N
o=A.B(A.j(["display","grid","grid-template-columns",A.ES(new A.nN(m,m,m,s,r,m,m,m)),"min-height","0","flex","1","align-items","start","overflow","visible"],o,o))
n=A.a([],q)
n.push(A.zf(A.a([s],q),m,"arcane-scaffold-sidebar",B.lG))
n.push(A.I4(A.a([r],q),"arcane-scaffold-main",B.kW))
p.push(new A.c(m,"arcane-scaffold-body",o,m,m,n,m))
return new A.c(m,"arcane-scaffold shadcn-scaffold",B.kz,m,m,p,m)}}
A.j8.prototype={
gkJ(){switch(0){case 0:break}return B.jG},
gkL(){switch(1){case 1:break}return B.jI},
gkM(){switch(1){case 1:break}return B.jH},
l(a){var s,r,q=this,p=null
A.b5(a)
s=q.gkJ()
r=q.gkL()
q.gkM()
return new A.l2(new A.ry(q.d,q.e,p,p,p,s,r,!1,p,p,A.eo(q)),p)}}
A.ja.prototype={
gkK(){switch(2){case 2:break}return B.jL},
gli(){switch(2){case 2:break}return B.jP},
l(a){var s=this,r=null
A.b5(a)
return new A.l3(new A.rR(r,s.e,s.x,s.gkK(),s.gli(),s.f,!0,s.y,r,r,r,!0,!0,!1,r,!0,!0,!0),r)}}
A.rQ.prototype={
E(){return"SheetPosition."+this.b}}
A.rS.prototype={
E(){return"SheetSize."+this.b}}
A.dG.prototype={
U(){return new A.i2()}}
A.i2.prototype={
aW(){this.bi()
this.a.toString
this.d=!1},
bq(a){this.c0(t.o5.a(a))
this.a.toString},
lx(){this.t(new A.u3(this))
this.a.toString},
l(a){var s,r,q,p
A.b5(a)
s=this.a
r=s.d
q=s.e
s=s.f
p=this.d
p===$&&A.S()
return new A.l4(new A.rT(r,q,s,p,280,64,!1,!1,this.glw()),null)}}
A.u3.prototype={
$0(){var s=this.a,r=s.d
r===$&&A.S()
s.d=!r},
$S:0}
A.eu.prototype={
U(){return new A.lS()}}
A.lS.prototype={
l(a){var s,r,q,p,o,n,m,l,k,j=this,i=null,h="link",g=j.a,f=g.e,e=g.d
g=t.aw
s=A.a([],g)
r=j.a.r
if(r.length!==0)B.b.m(s,new A.cc("title",i,r))
r=j.a.w
if(r.length!==0){q=t.N
B.b.m(s,new A.cc("meta",A.j(["name","description","content",r],q,q),i))}for(r=t.N,p=0;p<1;++p){o=B.dm[p]
if(B.a.v(o,"fonts.googleapis.com")){q=A.j(["href","https://fonts.googleapis.com","rel","preconnect"],r,r)
n=A.j(["href","https://fonts.gstatic.com","rel","preconnect","crossorigin",""],r,r)
B.b.B(s,A.a([new A.cc(h,q,i),new A.cc(h,n,i)],g))}B.b.m(s,new A.cc(h,A.j(["href",o,"rel","stylesheet"],r,r),i))}g=e.gmH()
m=A.Dt(e,":root, html.light, .light {\n  --card: #ffffff;\n  --card-foreground: #09090b;\n  --popover: #ffffff;\n  --popover-foreground: #09090b;\n  --muted: #f4f4f5;\n  --muted-foreground: #71717a;\n  --accent: #f4f4f5;\n  --accent-foreground: #18181b;\n  --border: #e4e4e7;\n  --input: #e4e4e7;\n}\n\nhtml.dark, .dark {\n  --background: #050505;\n  --foreground: #F7F4EC;\n  --card: #111111;\n  --card-foreground: #F7F4EC;\n  --card-hover: #1A1A1A;\n  --popover: #1A1A1A;\n  --popover-foreground: #F7F4EC;\n  --muted: #111111;\n  --muted-foreground: #A8A39A;\n  --accent: #1A1A1A;\n  --accent-foreground: #F7F4EC;\n  --border: #2C2C2C;\n  --input: #232323;\n}\n\n\n.arcane-button,\n.arcane-text-input,\n.arcane-select,\n.arcane-select-option,\n.arcane-dropdown-item,\n.arcane-context-menu-item,\n.arcane-tab,\n.arcane-tab-bar-item,\n.arcane-menubar-trigger,\n.arcane-dialog-close,\n.arcane-sheet-close,\n.arcane-drawer-close,\n.arcane-checkbox,\n.arcane-radio-circle,\n.arcane-toggle-switch,\n.arcane-pagination-link,\n.arcane-date-picker-trigger,\n.arcane-otp-digit,\n.arcane-calendar-day,\n.arcane-calendar-nav-btn {\n  transition:\n    color var(--transition),\n    background-color var(--transition),\n    border-color var(--transition),\n    box-shadow var(--transition),\n    opacity var(--transition),\n    transform var(--transition);\n}\n\n.arcane-button:hover:not(:disabled):not(.disabled),\n.arcane-text-input:hover:not(:disabled),\n.arcane-select:hover:not(:disabled):not(.disabled),\n.arcane-select-option:hover:not(:disabled):not(.disabled),\n.arcane-dropdown-item:hover:not(.disabled),\n.arcane-context-menu-item:hover:not(.disabled),\n.arcane-tab:hover:not(.disabled),\n.arcane-tab-bar-item:hover:not(.disabled),\n.arcane-menubar-trigger:hover,\n.arcane-dialog-close:hover,\n.arcane-sheet-close:hover,\n.arcane-drawer-close:hover,\n.arcane-pagination-link:hover:not(:disabled):not(.disabled),\n.arcane-date-picker-trigger:hover:not(:disabled),\n.arcane-calendar-day:hover:not(:disabled),\n.arcane-calendar-nav-btn:hover:not(:disabled) {\n  background-color: var(--accent);\n  color: var(--accent-foreground);\n}\n\n.arcane-button:focus-visible,\n.arcane-text-input:focus-visible,\n.arcane-select:focus-visible,\n.arcane-select-option:focus-visible,\n.arcane-dropdown-item:focus-visible,\n.arcane-context-menu-item:focus-visible,\n.arcane-tab:focus-visible,\n.arcane-tab-bar-item:focus-visible,\n.arcane-menubar-trigger:focus-visible,\n.arcane-dialog-close:focus-visible,\n.arcane-sheet-close:focus-visible,\n.arcane-drawer-close:focus-visible,\n.arcane-checkbox:focus-visible,\n.arcane-radio-input:focus-visible + .arcane-radio-circle,\n.arcane-toggle-switch:focus-visible,\n.arcane-pagination-link:focus-visible,\n.arcane-date-picker-trigger:focus-visible,\n.arcane-otp-digit:focus-visible,\n.arcane-calendar-day:focus-visible,\n.arcane-calendar-nav-btn:focus-visible {\n  outline: none;\n  box-shadow: 0 0 0 2px var(--background), 0 0 0 4px var(--ring);\n}\n\n.arcane-button:disabled,\n.arcane-button[data-disabled='true'],\n.arcane-button.disabled,\n.arcane-text-input:disabled,\n.arcane-text-input[data-disabled='true'],\n.arcane-select:disabled,\n.arcane-select[data-disabled='true'],\n.arcane-select.disabled,\n.arcane-select-option:disabled,\n.arcane-select-option[data-disabled='true'],\n.arcane-select-option.disabled,\n.arcane-dropdown-item[data-disabled='true'],\n.arcane-dropdown-item.disabled,\n.arcane-context-menu-item[data-disabled='true'],\n.arcane-context-menu-item.disabled,\n.arcane-tab[data-disabled='true'],\n.arcane-tab.disabled,\n.arcane-tab-bar-item[data-disabled='true'],\n.arcane-tab-bar-item.disabled,\n.arcane-menubar-trigger[data-disabled='true'],\n.arcane-checkbox[data-disabled='true'],\n.arcane-radio-item[data-disabled='true'],\n.arcane-toggle-switch[data-disabled='true'],\n.arcane-pagination-link:disabled,\n.arcane-pagination-link[data-disabled='true'],\n.arcane-pagination-link.disabled,\n.arcane-date-picker-trigger:disabled,\n.arcane-date-picker-trigger[data-disabled='true'],\n.arcane-otp-digit:disabled,\n.arcane-otp-digit[data-disabled='true'],\n.arcane-calendar-day:disabled,\n.arcane-calendar-day[data-disabled='true'],\n.arcane-calendar-nav-btn:disabled {\n  pointer-events: none;\n  opacity: 0.5;\n}\n\n.arcane-select[data-open='true'],\n.arcane-dropdown-menu[data-state='open'],\n.arcane-menubar-trigger[data-state='open'],\n.arcane-date-picker-trigger[data-state='open'],\n.arcane-tab[data-state='active'],\n.arcane-tab-bar-item[data-state='active'],\n.arcane-select-option[data-state='checked'],\n.arcane-dropdown-item[data-state='checked'],\n.arcane-context-menu-item[data-state='checked'],\n.arcane-menubar-item[data-state='checked'],\n.arcane-checkbox[data-state='checked'],\n.arcane-toggle-switch[data-state='checked'] {\n  background-color: var(--accent);\n  color: var(--accent-foreground);\n}\n\n.arcane-select[data-open='true'],\n.arcane-date-picker-trigger[data-state='open'],\n.arcane-text-input[data-error='true'],\n.arcane-select[data-error='true'],\n.arcane-otp-digit.error,\n.arcane-calendar-day[data-state='selected'] {\n  border-color: var(--ring);\n}\n\n/* ============================================\n   PROSE - ShadCN Clean Typography\n   ============================================ */\n.prose {\n  max-width: 65ch;\n  color: var(--foreground);\n  line-height: 1.75;\n}\n\n.prose h1, .prose h2, .prose h3,\n.prose h4, .prose h5, .prose h6 {\n  color: var(--foreground);\n  font-weight: 600;\n  line-height: 1.25;\n  margin-top: 2rem;\n  margin-bottom: 1rem;\n}\n\n.prose h1 { font-size: 2.25rem; margin-top: 0; }\n.prose h2 {\n  font-size: 1.5rem;\n  border-bottom: 1px solid var(--border);\n  padding-bottom: 0.5rem;\n}\n.prose h3 { font-size: 1.25rem; }\n.prose h4 { font-size: 1.125rem; }\n\n.prose p {\n  margin-bottom: 1.25rem;\n}\n\n.prose a {\n  color: var(--primary);\n  text-decoration: underline;\n  text-underline-offset: 2px;\n  transition: color 0.15s ease;\n}\n\n.prose a:hover {\n  opacity: 0.8;\n}\n\n.prose strong, .prose b {\n  font-weight: 600;\n}\n\n.prose ul, .prose ol {\n  margin-bottom: 1.25rem;\n  padding-left: 1.5rem;\n}\n\n.prose li {\n  margin-bottom: 0.5rem;\n}\n\n.prose li::marker {\n  color: var(--muted-foreground);\n}\n\n.prose blockquote {\n  border-left: 4px solid var(--border);\n  padding-left: 1rem;\n  margin: 1.5rem 0;\n  font-style: italic;\n  color: var(--muted-foreground);\n}\n\n.prose hr {\n  border: none;\n  border-top: 1px solid var(--border);\n  margin: 2rem 0;\n}\n\n.prose table {\n  width: 100%;\n  border-collapse: collapse;\n  margin: 1.5rem 0;\n}\n\n.prose th, .prose td {\n  border: 1px solid var(--border);\n  padding: 0.75rem;\n  text-align: left;\n}\n\n.prose th {\n  background: var(--muted);\n  font-weight: 600;\n}\n\n.prose img {\n  max-width: 100%;\n  height: auto;\n  border-radius: var(--radius-md);\n  margin: 1.5rem 0;\n}\n\n/* Code blocks */\n.prose pre {\n  background: var(--muted);\n  border: 1px solid var(--border);\n  border-radius: var(--radius-md);\n  padding: 1rem 1.25rem;\n  overflow-x: auto;\n  margin: 1.5rem 0;\n}\n\n.prose code {\n  font-family: var(--font-mono);\n  font-size: 0.875em;\n}\n\n.prose :not(pre) > code {\n  background: var(--muted);\n  padding: 0.125rem 0.375rem;\n  border-radius: var(--radius-sm);\n  font-size: 0.875em;\n}\n\n/* Syntax highlighting - Light */\n.prose .hljs-keyword { color: #d73a49; }\n.prose .hljs-string { color: #032f62; }\n.prose .hljs-number { color: #005cc5; }\n.prose .hljs-function, .prose .hljs-title { color: #6f42c1; }\n.prose .hljs-comment { color: #6a737d; font-style: italic; }\n.prose .hljs-variable { color: #e36209; }\n.prose .hljs-class, .prose .hljs-built_in { color: #22863a; }\n\n/* Syntax highlighting - Dark */\n.dark .prose .hljs-keyword { color: #ff7b72; }\n.dark .prose .hljs-string { color: #a5d6ff; }\n.dark .prose .hljs-number { color: #79c0ff; }\n.dark .prose .hljs-function, .dark .prose .hljs-title { color: #d2a8ff; }\n.dark .prose .hljs-comment { color: #8b949e; font-style: italic; }\n.dark .prose .hljs-variable { color: #ffa657; }\n.dark .prose .hljs-class, .dark .prose .hljs-built_in { color: #7ee787; }\n\n/* Tree Lines for Disclosure/Navigation\n   Each item draws its own connectors:\n   - ::before = horizontal branch to content\n   - ::after = vertical line down to next sibling (except last item = L-connector)\n*/\n.arcane-tree-lines {\n  position: relative;\n  --tree-indent: 1rem;\n  --tree-line-color: var(--border);\n}\n\n/* Each direct child is a tree item */\n.arcane-tree-lines > * {\n  position: relative;\n  padding-left: var(--tree-indent);\n}\n\n/* Horizontal branch from vertical line to content */\n.arcane-tree-lines > *::before {\n  content: '';\n  position: absolute;\n  left: 0;\n  top: 50%;\n  width: calc(var(--tree-indent) - 4px);\n  height: 1px;\n  background: var(--tree-line-color);\n}\n\n/* Vertical line segment - connects this item to the next */\n.arcane-tree-lines > *::after {\n  content: '';\n  position: absolute;\n  left: 0;\n  top: 0;\n  bottom: 0;\n  width: 1px;\n  background: var(--tree-line-color);\n}\n\n/* Last item: L-connector - vertical line only goes to the horizontal branch */\n.arcane-tree-lines > *:last-child::after {\n  bottom: 50%;\n}\n\n/* First item: start vertical line from horizontal branch */\n.arcane-tree-lines > *:first-child::after {\n  top: 50%;\n}\n\n/* Only child: just horizontal branch, no vertical */\n.arcane-tree-lines > *:only-child::after {\n  display: none;\n}\n\n/* Nested tree lines - progressively lighter for visual hierarchy */\n.arcane-tree-lines .arcane-tree-lines {\n  --tree-line-color: color-mix(in srgb, var(--border) 70%, transparent);\n}\n\n.arcane-tree-lines .arcane-tree-lines .arcane-tree-lines {\n  --tree-line-color: color-mix(in srgb, var(--border) 50%, transparent);\n}\n\n.arcane-tree-lines .arcane-tree-lines .arcane-tree-lines .arcane-tree-lines {\n  --tree-line-color: color-mix(in srgb, var(--border) 35%, transparent);\n}\n\n/* ============================================\n   SIDEBAR HEADER & BRAND - ShadCN Style (Default)\n   Clean, minimal design with clear hierarchy\n   ============================================ */\n.sidebar-header {\n  padding: 1rem;\n  border-bottom: 1px solid var(--border);\n  display: flex;\n  flex-direction: column;\n  gap: 0.875rem;\n}\n\n.sidebar-brand {\n  padding-bottom: 0;\n}\n\n.sidebar-brand-link {\n  text-decoration: none;\n}\n\n.sidebar-brand-title {\n  font-weight: 700;\n  font-size: 1.0625rem;\n  color: var(--foreground);\n  letter-spacing: -0.01em;\n  line-height: 1.2;\n}\n\n.sidebar-brand-subtitle {\n  font-size: 0.6875rem;\n  color: var(--muted-foreground);\n  margin-top: 0.125rem;\n  letter-spacing: 0.02em;\n}\n\n/* Navigation tabs */\n.sidebar-tabs {\n  display: flex;\n  gap: 0.25rem;\n  padding: 0.25rem;\n  background: var(--muted);\n  border-radius: var(--radius-md);\n}\n\n.sidebar-tab {\n  flex: 1;\n  padding: 0.375rem 0.5rem;\n  font-size: 0.75rem;\n  font-weight: 500;\n  color: var(--muted-foreground);\n  text-decoration: none;\n  text-align: center;\n  border-radius: calc(var(--radius-md) - 2px);\n  transition: color 0.15s ease, background 0.15s ease;\n}\n\n.sidebar-tab:hover {\n  color: var(--foreground);\n}\n\n.sidebar-tab.active {\n  background: var(--background);\n  color: var(--foreground);\n  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);\n}\n\n/* Search and controls row */\n.sidebar-controls {\n  display: flex;\n  gap: 0.5rem;\n  align-items: center;\n}\n\n.sidebar-search {\n  flex: 1;\n  position: relative;\n}\n\n.sidebar-search input {\n  width: 100%;\n  padding: 0.5rem 0.75rem 0.5rem 2rem;\n  font-size: 0.8125rem;\n  color: var(--foreground);\n  background: var(--muted);\n  border: 1px solid transparent;\n  border-radius: var(--radius-md);\n  outline: none;\n  transition: border-color 0.15s ease, background 0.15s ease;\n}\n\n.sidebar-search input::placeholder {\n  color: var(--muted-foreground);\n}\n\n.sidebar-search input:focus {\n  border-color: var(--ring);\n  background: var(--background);\n}\n\n.sidebar-search .search-icon {\n  position: absolute;\n  left: 0.625rem;\n  top: 50%;\n  transform: translateY(-50%);\n  color: var(--muted-foreground);\n  pointer-events: none;\n}\n\n/* Theme toggle button */\n.sidebar-theme-toggle {\n  width: 36px;\n  height: 36px;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  background: var(--muted);\n  border: none;\n  border-radius: var(--radius-md);\n  color: var(--muted-foreground);\n  cursor: pointer;\n  transition: color 0.15s ease, background 0.15s ease;\n}\n\n.sidebar-theme-toggle:hover {\n  color: var(--foreground);\n  background: color-mix(in srgb, var(--muted) 150%, transparent);\n}\n\n/* Show/hide icons based on theme */\n.dark .theme-icon-dark { display: none; }\n.dark .theme-icon-light { display: block; }\n:not(.dark) .theme-icon-dark { display: block; }\n:not(.dark) .theme-icon-light { display: none; }\n\n/* ============================================\n   SIDEBAR SECTIONS - ShadCN Visual Styles\n   ============================================ */\n.sidebar-section-header {\n  font-size: 0.75rem;\n  font-weight: 600;\n  color: var(--muted-foreground);\n  text-transform: uppercase;\n  letter-spacing: 0.05em;\n}\n\n/* Folder summaries - ShadCN visual styling */\n.sidebar-summary {\n  font-size: 0.8125rem;\n  font-weight: 600;\n  color: var(--foreground);\n  background: transparent;\n  border: none;\n  border-radius: var(--radius-md, 6px);\n  transition: all 0.2s ease;\n}\n\n.sidebar-summary:hover {\n  background: var(--muted);\n  color: var(--foreground);\n}\n\n/* Expanded folder - ShadCN subtle highlight */\n.sidebar-details[open] > .sidebar-summary {\n  background: var(--muted);\n  color: var(--foreground);\n}\n\n.sidebar-details[open] > .sidebar-summary:hover {\n  background: color-mix(in srgb, var(--muted) 150%, transparent);\n}\n\n/* Chevron visual styling - ShadCN */\n.sidebar-chevron {\n  opacity: 0.6;\n}\n\n.sidebar-summary:hover .sidebar-chevron {\n  opacity: 1;\n}\n\n.sidebar-details[open] > .sidebar-summary .sidebar-chevron {\n  opacity: 1;\n}\n\n/* Nested folder styling - decreasing opacity */\n.sidebar-tree .sidebar-summary {\n  font-size: 0.8125rem;\n  color: var(--muted-foreground);\n  opacity: 0.9;\n}\n\n.sidebar-tree .sidebar-tree .sidebar-summary {\n  opacity: 0.85;\n}\n\n.sidebar-tree .sidebar-tree .sidebar-tree .sidebar-summary {\n  opacity: 0.8;\n}\n\n/* Tree connector colors - ShadCN (var(--border)) */\n.sidebar-tree > .sidebar-section::before,\n.sidebar-tree > .sidebar-section::after {\n  background: var(--border);\n}\n\n/* ============================================\n   SIDEBAR NAVIGATION - Tree Lines\n   Supports leaves (links) and folders (disclosures)\n   ============================================ */\n.sidebar-tree-nav {\n  position: relative;\n}\n\n.sidebar-tree {\n  position: relative;\n  display: flex;\n  flex-direction: column;\n  padding-left: 0.75rem;\n  margin-left: 0.5rem;\n  margin-top: 0.25rem;\n}\n\n.sidebar-tree-items {\n  position: relative;\n  display: flex;\n  flex-direction: column;\n  padding-left: 0.75rem;\n  margin-left: 0.5rem;\n  margin-top: 0.25rem;\n}\n\n/* Tree item with horizontal branch and vertical connector */\n.sidebar-tree-item {\n  position: relative;\n}\n\n/* Horizontal branch line */\n.sidebar-tree-item::before {\n  content: '';\n  position: absolute;\n  left: -0.75rem;\n  top: 50%;\n  width: 0.5rem;\n  height: 1px;\n  background: var(--border);\n}\n\n/* Vertical line segment - connects this item to the next (not on last item) */\n.sidebar-tree-item:not(:last-child)::after {\n  content: '';\n  position: absolute;\n  left: -0.75rem;\n  top: 0;\n  bottom: 0;\n  width: 1px;\n  background: var(--border);\n}\n\n/* First item extends vertical line up to connect to parent */\n.sidebar-tree-item:first-child::after {\n  top: 0;\n}\n\n/* Last item only draws vertical line from top to center (L-bend) */\n.sidebar-tree-item:last-child::after {\n  content: '';\n  position: absolute;\n  left: -0.75rem;\n  top: 0;\n  height: 50%;\n  width: 1px;\n  background: var(--border);\n}\n\n/* Single child - hide tree lines, show dot */\n.sidebar-tree-items:has(> .sidebar-tree-item:only-child) > .sidebar-tree-item::before,\n.sidebar-tree-items:has(> .sidebar-tree-item:only-child) > .sidebar-tree-item::after {\n  display: none;\n}\n\n/* Nested details within tree - inherits tree styling */\n.sidebar-tree .sidebar-details,\n.sidebar-tree-items .sidebar-details {\n  margin-left: -0.75rem;\n}\n\n.sidebar-tree .sidebar-details .sidebar-summary,\n.sidebar-tree-items .sidebar-details .sidebar-summary {\n  padding: 0.25rem 0.5rem;\n  font-size: 0.75rem;\n  font-weight: 500;\n  text-transform: none;\n  letter-spacing: normal;\n}\n\n.sidebar-tree .sidebar-details .sidebar-tree,\n.sidebar-tree-items .sidebar-details .sidebar-tree-items {\n  margin-left: 0.5rem;\n  padding-left: 0.75rem;\n  margin-top: 0;\n}\n\n/* Navigation link styling */\n.sidebar-link {\n  display: block;\n  padding: 0.375rem 0.625rem;\n  font-size: 0.8125rem;\n  color: var(--muted-foreground);\n  text-decoration: none;\n  border-radius: var(--radius-sm);\n  transition: color 0.15s ease, background 0.15s ease;\n}\n\n.sidebar-link:hover {\n  color: var(--foreground);\n  background: var(--muted);\n}\n\n/* Active state */\n.sidebar-link.active {\n  color: var(--accent-foreground);\n  font-weight: 500;\n  background: var(--accent);\n}\n\n/* Collapsible section styles */\n.sidebar-details {\n  border: none;\n}\n\n.sidebar-details > summary {\n  list-style: none;\n}\n\n.sidebar-details > summary::-webkit-details-marker {\n  display: none;\n}\n\n.sidebar-summary {\n  display: flex;\n  align-items: center;\n  gap: 0.5rem;\n  padding: 0.5rem 0.75rem;\n  font-size: 0.75rem;\n  font-weight: 500;\n  text-transform: uppercase;\n  letter-spacing: 0.025em;\n  color: var(--muted-foreground);\n  cursor: pointer;\n  border-radius: var(--radius-sm);\n  transition: color 0.15s ease, background 0.15s ease;\n  user-select: none;\n}\n\n.sidebar-summary:hover {\n  color: var(--foreground);\n  background: var(--muted);\n}\n\n/* Chevron icon for collapsible */\n.sidebar-chevron {\n  margin-left: auto;\n  width: 14px;\n  height: 14px;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  transition: transform 0.15s ease;\n}\n\n.sidebar-chevron::before {\n  content: '';\n  width: 5px;\n  height: 5px;\n  border-right: 1.5px solid var(--muted-foreground);\n  border-bottom: 1.5px solid var(--muted-foreground);\n  transform: rotate(-45deg);\n  transition: transform 0.15s ease;\n}\n\n.sidebar-details[open] .sidebar-chevron::before {\n  transform: rotate(45deg);\n}\n\n/* Nested depth styling - fading lines */\n.sidebar-tree-items .sidebar-tree-items .sidebar-tree-item::before,\n.sidebar-tree-items .sidebar-tree-items .sidebar-tree-item::after {\n  background: color-mix(in srgb, var(--border) 70%, transparent);\n}\n\n.sidebar-tree-items .sidebar-tree-items .sidebar-tree-items .sidebar-tree-item::before,\n.sidebar-tree-items .sidebar-tree-items .sidebar-tree-items .sidebar-tree-item::after {\n  background: color-mix(in srgb, var(--border) 50%, transparent);\n}\n\n/* No tree lines variant */\n.sidebar-tree-items.no-tree-lines .sidebar-tree-item::before,\n.sidebar-tree-items.no-tree-lines .sidebar-tree-item::after,\n.sidebar-tree-items.no-tree-lines .sidebar-details::before {\n  display: none;\n}\n\n/* Sidebar hover enhancement */\naside a:hover {\n  background: var(--muted) !important;\n}\n\n\n/* ============================================\n   ARCANE SIDEBAR COMPONENTS - Tree Lines\n   Tree-line styling for ArcaneSidebar components\n   ============================================ */\n\n/* Submenu content and group items containers get tree-line treatment */\n.arcane-sidebar-submenu-content,\n.arcane-sidebar-group-items {\n  position: relative;\n  display: flex;\n  flex-direction: column;\n}\n\n/* Each sidebar item in a submenu or group gets tree connectors */\n.arcane-sidebar-submenu-content > .arcane-sidebar-item,\n.arcane-sidebar-group-items > .arcane-sidebar-item {\n  position: relative;\n}\n\n/* Horizontal branch line */\n.arcane-sidebar-submenu-content > .arcane-sidebar-item::before,\n.arcane-sidebar-group-items > .arcane-sidebar-item::before {\n  content: '';\n  position: absolute;\n  left: 0;\n  top: 50%;\n  width: 0.5rem;\n  height: 1px;\n  background: var(--border);\n}\n\n/* Vertical line segment - connects this item to the next */\n.arcane-sidebar-submenu-content > .arcane-sidebar-item:not(:last-child)::after,\n.arcane-sidebar-group-items > .arcane-sidebar-item:not(:last-child)::after {\n  content: '';\n  position: absolute;\n  left: 0;\n  top: 0;\n  bottom: 0;\n  width: 1px;\n  background: var(--border);\n}\n\n/* Last item - L-bend connector */\n.arcane-sidebar-submenu-content > .arcane-sidebar-item:last-child::after,\n.arcane-sidebar-group-items > .arcane-sidebar-item:last-child::after {\n  content: '';\n  position: absolute;\n  left: 0;\n  top: 0;\n  height: 50%;\n  width: 1px;\n  background: var(--border);\n}\n\n/* Single child - hide tree lines */\n.arcane-sidebar-submenu-content:has(> .arcane-sidebar-item:only-child) > .arcane-sidebar-item::before,\n.arcane-sidebar-submenu-content:has(> .arcane-sidebar-item:only-child) > .arcane-sidebar-item::after,\n.arcane-sidebar-group-items:has(> .arcane-sidebar-item:only-child) > .arcane-sidebar-item::before,\n.arcane-sidebar-group-items:has(> .arcane-sidebar-item:only-child) > .arcane-sidebar-item::after {\n  display: none;\n}\n\n/* Adjust padding to accommodate tree lines */\n.arcane-sidebar-submenu-content > .arcane-sidebar-item,\n.arcane-sidebar-group-items > .arcane-sidebar-item {\n  padding-left: 1rem !important;\n}\n\n\n\n/* ============================================\n   ARCANE MAP - Base Styles (ShadCN)\n   ============================================ */\n\n/* Map container */\n.arcane-world-map,\n.arcane-usa-map {\n  position: relative;\n  width: 100%;\n  background: var(--card);\n  border: 1px solid var(--border);\n  border-radius: var(--radius);\n  overflow: hidden;\n}\n\n/* Region paths */\n.arcane-world-map path[data-region],\n.arcane-usa-map path[data-region] {\n  transition: fill 150ms ease, opacity 150ms ease;\n}\n\n.arcane-world-map path[data-region]:hover,\n.arcane-usa-map path[data-region]:hover {\n  fill: var(--accent) !important;\n  opacity: 0.9;\n}\n\n/* Location pins */\n.arcane-world-map circle[data-location],\n.arcane-usa-map circle[data-location] {\n  transition: fill 150ms ease, r 150ms ease;\n}\n\n.arcane-world-map circle[data-location]:hover,\n.arcane-usa-map circle[data-location]:hover {\n  fill: var(--primary) !important;\n}\n\n/* Debug tooltip */\n.arcane-map-debug-tooltip {\n  position: absolute;\n  z-index: 9999;\n  pointer-events: none;\n}\n\n.arcane-map-debug-tooltip > div {\n  background: var(--popover);\n  border: 1px solid var(--border);\n  border-radius: var(--radius);\n  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);\n  padding: 10px 14px;\n}\n\n.arcane-map-debug-tooltip .debug-lat,\n.arcane-map-debug-tooltip .debug-lng {\n  font-size: 13px;\n  font-family: var(--font-mono, ui-monospace, monospace);\n  color: var(--popover-foreground);\n  white-space: nowrap;\n  font-weight: 500;\n}\n\n.arcane-map-debug-tooltip .debug-svg {\n  font-size: 11px;\n  font-family: var(--font-mono, ui-monospace, monospace);\n  color: var(--muted-foreground);\n  white-space: nowrap;\n  margin-top: 6px;\n}\n\n.arcane-map-debug-tooltip .debug-hint {\n  font-size: 11px;\n  color: var(--primary);\n  margin-top: 6px;\n  font-weight: 500;\n}\n\n\n\n/* ============================================\n   TABLE OF CONTENTS - Tree Line Styles\n   Clean tree view with subtle connecting lines\n   ============================================ */\n\n/* Scrollbar styling for TOC container */\n.kb-toc,\n.toc-container {\n  scrollbar-width: thin;\n  scrollbar-color: var(--border) transparent;\n}\n\n.kb-toc::-webkit-scrollbar,\n.toc-container::-webkit-scrollbar {\n  width: 4px;\n}\n\n.kb-toc::-webkit-scrollbar-track,\n.toc-container::-webkit-scrollbar-track {\n  background: transparent;\n}\n\n.kb-toc::-webkit-scrollbar-thumb,\n.toc-container::-webkit-scrollbar-thumb {\n  background: var(--border);\n  border-radius: 2px;\n}\n\n/* TOC content with tree lines */\n.toc-content ul {\n  list-style: none;\n  padding-left: 0;\n  margin: 0;\n  position: relative;\n}\n\n/* Top-level list gets tree line padding */\n.toc-content > ul {\n  padding-left: 0.75rem;\n  margin-left: 0.25rem;\n}\n\n/* Nested lists */\n.toc-content ul ul {\n  padding-left: 0.875rem;\n  margin-left: 0.375rem;\n  margin-top: 0.25rem;\n  position: relative;\n}\n\n/* All list items */\n.toc-content li {\n  position: relative;\n}\n\n/* Horizontal branch for top-level items */\n.toc-content > ul > li::before {\n  content: '';\n  position: absolute;\n  left: -0.5rem;\n  top: 50%;\n  width: 0.375rem;\n  height: 1px;\n  background: var(--border);\n}\n\n/* Vertical line segment for top-level items (not last) */\n.toc-content > ul > li:not(:last-child)::after {\n  content: '';\n  position: absolute;\n  left: -0.5rem;\n  top: 0;\n  bottom: 0;\n  width: 1px;\n  background: var(--border);\n}\n\n/* Last top-level item - vertical line only to center (L-bend) */\n.toc-content > ul > li:last-child::after {\n  content: '';\n  position: absolute;\n  left: -0.5rem;\n  top: 0;\n  height: 50%;\n  width: 1px;\n  background: var(--border);\n}\n\n/* Horizontal branch for nested items */\n.toc-content ul ul li::before {\n  content: '';\n  position: absolute;\n  left: -0.875rem;\n  top: 50%;\n  width: 0.5rem;\n  height: 1px;\n  background: var(--border);\n}\n\n/* Vertical line segment for nested items (not last) */\n.toc-content ul ul li:not(:last-child)::after {\n  content: '';\n  position: absolute;\n  left: -0.875rem;\n  top: 0;\n  bottom: 0;\n  width: 1px;\n  background: var(--border);\n}\n\n/* Last nested item - vertical line only to center (L-bend) */\n.toc-content ul ul li:last-child::after {\n  content: '';\n  position: absolute;\n  left: -0.875rem;\n  top: 0;\n  height: 50%;\n  width: 1px;\n  background: var(--border);\n}\n\n/* Single child - show dot instead of tree lines */\n.toc-content > ul:has(> li:only-child) > li::before,\n.toc-content > ul:has(> li:only-child) > li::after {\n  display: none;\n}\n\n.toc-content ul ul:has(> li:only-child) > li::before,\n.toc-content ul ul:has(> li:only-child) > li::after {\n  display: none;\n}\n\n/* Link styling */\n.toc-content a {\n  color: var(--muted-foreground);\n  text-decoration: none;\n  font-size: 0.8125rem;\n  line-height: 1.3;\n  display: block;\n  margin-left: 0.125rem;\n  padding: 0.5rem 0.75rem 0.5rem 0.625rem !important;\n  border-radius: var(--radius-sm);\n  transition: color 0.15s ease, background 0.15s ease;\n}\n\n.toc-content a:hover {\n  color: var(--foreground);\n  background: var(--muted);\n}\n\n/* Active TOC link */\n.toc-content a.toc-active {\n  color: var(--foreground);\n  font-weight: 500;\n  background: var(--muted);\n}\n\n/* Nested link sizing */\n.toc-content ul ul a {\n  font-size: 0.8125rem;\n  line-height: 1.3;\n  padding: 0.5rem 0.75rem 0.5rem 0.625rem !important;\n}\n\n/* Fading tree lines at deeper nesting levels */\n.toc-content ul ul li::before,\n.toc-content ul ul li::after {\n  background: color-mix(in srgb, var(--border) 70%, transparent);\n}\n\n.toc-content ul ul ul li::before,\n.toc-content ul ul ul li::after {\n  background: color-mix(in srgb, var(--border) 50%, transparent);\n}\n\n\n\n#arcane-root.arcane-theme-shadcn {\n  --shadcn-subtle-line: color-mix(in srgb, var(--border) 52%, transparent);\n  --shadcn-hairline: color-mix(in srgb, var(--border) 38%, transparent);\n  --shadcn-panel-fill: color-mix(in srgb, var(--background) 90%, var(--secondary));\n  --shadcn-panel-highlight: color-mix(in srgb, var(--primary) 9%, var(--background));\n  --shadcn-control-fill: color-mix(in srgb, var(--accent) 52%, var(--background));\n  --shadcn-control-hover: color-mix(in srgb, var(--accent) 74%, var(--background));\n}\n\nhtml.dark #arcane-root.arcane-theme-shadcn,\n#arcane-root.dark.arcane-theme-shadcn {\n  --shadcn-subtle-line: color-mix(in srgb, var(--border) 64%, transparent);\n  --shadcn-hairline: color-mix(in srgb, var(--border) 46%, transparent);\n  --shadcn-panel-fill: color-mix(in srgb, var(--background) 82%, var(--secondary));\n  --shadcn-panel-highlight: color-mix(in srgb, var(--primary) 14%, var(--background));\n  --shadcn-control-fill: color-mix(in srgb, var(--accent) 72%, var(--background));\n  --shadcn-control-hover: color-mix(in srgb, var(--accent) 84%, var(--primary));\n}\n\nhtml:has(#arcane-root.arcane-theme-shadcn),\nhtml:has(#arcane-root.arcane-theme-shadcn) body,\n#arcane-root.arcane-theme-shadcn,\n#arcane-root.arcane-theme-shadcn * {\n  scrollbar-width: thin;\n  scrollbar-color: color-mix(in srgb, var(--border) 72%, transparent) transparent;\n}\n\nhtml:has(#arcane-root.arcane-theme-shadcn)::-webkit-scrollbar,\nhtml:has(#arcane-root.arcane-theme-shadcn) body::-webkit-scrollbar,\n#arcane-root.arcane-theme-shadcn *::-webkit-scrollbar {\n  width: 0.5rem;\n  height: 0.5rem;\n}\n\nhtml:has(#arcane-root.arcane-theme-shadcn)::-webkit-scrollbar-track,\nhtml:has(#arcane-root.arcane-theme-shadcn) body::-webkit-scrollbar-track,\n#arcane-root.arcane-theme-shadcn *::-webkit-scrollbar-track {\n  background: transparent;\n}\n\nhtml:has(#arcane-root.arcane-theme-shadcn)::-webkit-scrollbar-thumb,\nhtml:has(#arcane-root.arcane-theme-shadcn) body::-webkit-scrollbar-thumb,\n#arcane-root.arcane-theme-shadcn *::-webkit-scrollbar-thumb {\n  background: color-mix(in srgb, var(--border) 72%, transparent);\n  border: 2px solid transparent;\n  border-radius: 999px;\n  background-clip: padding-box;\n}\n\nhtml:has(#arcane-root.arcane-theme-shadcn)::-webkit-scrollbar-thumb:hover,\nhtml:has(#arcane-root.arcane-theme-shadcn) body::-webkit-scrollbar-thumb:hover,\n#arcane-root.arcane-theme-shadcn *::-webkit-scrollbar-thumb:hover {\n  background: color-mix(in srgb, var(--foreground) 28%, var(--border));\n}\n\n#arcane-root.arcane-theme-shadcn .arcane-scaffold {\n  min-height: 100vh !important;\n  display: flex !important;\n  flex-direction: column !important;\n  padding-top: 0 !important;\n  background: var(--background) !important;\n  color: var(--foreground) !important;\n}\n\n#arcane-root.arcane-theme-shadcn .arcane-scaffold-header {\n  position: sticky !important;\n  top: 0 !important;\n  left: auto !important;\n  right: auto !important;\n  z-index: 40 !important;\n  height: 3.5rem !important;\n  min-height: 3.5rem !important;\n  padding: 0 !important;\n  border-bottom: 1px solid var(--shadcn-hairline) !important;\n  border-bottom-color: var(--shadcn-hairline) !important;\n  background: color-mix(in srgb, var(--background) 94%, transparent) !important;\n  box-shadow: none !important;\n  backdrop-filter: blur(10px) saturate(1.08) !important;\n  -webkit-backdrop-filter: blur(10px) saturate(1.08) !important;\n}\n\n#arcane-root.arcane-theme-shadcn .arcane-scaffold-body {\n  display: grid !important;\n  grid-template-columns: minmax(15rem, 17.5rem) minmax(0, 1fr) !important;\n  align-items: start !important;\n  gap: 0 !important;\n  padding: 0 !important;\n  overflow: visible !important;\n}\n\n#arcane-root.arcane-theme-shadcn .arcane-scaffold-sidebar,\n#arcane-root.arcane-theme-shadcn .arcane-scaffold-secondary {\n  border-color: var(--shadcn-hairline) !important;\n  background: var(--shadcn-panel-fill) !important;\n  box-shadow: none !important;\n}\n\n#arcane-root.arcane-theme-shadcn .arcane-scaffold-sidebar.arcane-scaffold-sidebar {\n  position: sticky !important;\n  top: 3.5rem !important;\n  left: auto !important;\n  right: auto !important;\n  bottom: auto !important;\n  align-self: start !important;\n  width: 17.5rem !important;\n  border-right: 1px solid var(--shadcn-hairline) !important;\n  height: max-content !important;\n  max-height: none !important;\n  min-height: 0 !important;\n  overflow: visible !important;\n  padding: 0 !important;\n}\n\n#arcane-root.arcane-theme-shadcn .arcane-scaffold-main.arcane-scaffold-main {\n  min-width: 0 !important;\n  width: 100% !important;\n  max-width: none !important;\n  min-height: 0 !important;\n  margin-left: 0 !important;\n  padding: 0 !important;\n  border: 0 !important;\n  overflow: visible !important;\n}\n\n#arcane-root.arcane-theme-shadcn .kb-topbar {\n  position: sticky;\n  top: 0;\n  z-index: 50;\n  border: 0;\n  border-bottom: 1px solid var(--shadcn-hairline);\n  background: var(--background);\n  box-shadow: none;\n}\n\n#arcane-root.arcane-theme-shadcn .arcane-scaffold-header .kb-topbar {\n  border-bottom: 0 !important;\n}\n\n#arcane-root.arcane-theme-shadcn .kb-topbar-inner {\n  width: 100%;\n  max-width: none;\n  height: 3.5rem;\n  min-height: 3.5rem;\n  padding: 0 1.5rem;\n  gap: 1.25rem;\n}\n\n#arcane-root.arcane-theme-shadcn .kb-topbar-left,\n#arcane-root.arcane-theme-shadcn .kb-topbar-right {\n  min-width: 0;\n  gap: 0.875rem;\n}\n\n#arcane-root.arcane-theme-shadcn .kb-topbar-left {\n  flex: 1 1 auto;\n}\n\n#arcane-root.arcane-theme-shadcn .kb-topbar-right {\n  flex: 0 1 auto;\n  padding: 0;\n  border: 0;\n  border-radius: 0;\n  background: transparent;\n}\n\n#arcane-root.arcane-theme-shadcn .kb-topbar-nav {\n  min-width: 0;\n  margin-left: 0.25rem;\n  padding: 0;\n  gap: 1.25rem;\n  border: 0;\n  border-radius: 0;\n  background: transparent;\n}\n\n#arcane-root.arcane-theme-shadcn .kb-topbar-brand {\n  height: auto;\n  padding: 0;\n  gap: 0;\n  border: 0;\n  border-radius: 0;\n  background: transparent;\n  color: var(--foreground);\n  box-shadow: none;\n  font-size: 0.875rem;\n  font-weight: 600;\n  line-height: 1;\n  text-decoration: none;\n}\n\n#arcane-root.arcane-theme-shadcn .kb-topbar-brand-icon {\n  display: none;\n}\n\n#arcane-root.arcane-theme-shadcn .kb-topbar-brand-label {\n  color: var(--foreground);\n}\n\n#arcane-root.arcane-theme-shadcn .kb-style-switcher {\n  flex: 0 0 auto;\n  flex-wrap: nowrap;\n}\n\n#arcane-root.arcane-theme-shadcn .kb-topbar-link {\n  position: relative;\n  height: auto;\n  padding: 0.125rem 0;\n  border: 0;\n  border-radius: 0;\n  background: transparent;\n  color: var(--muted-foreground);\n  box-shadow: none;\n  font-size: 0.875rem;\n  font-weight: 500;\n  line-height: 1;\n  text-decoration: none;\n}\n\n#arcane-root.arcane-theme-shadcn .kb-topbar-link:hover,\n#arcane-root.arcane-theme-shadcn .kb-topbar-link.active {\n  background: transparent;\n  color: var(--foreground);\n  box-shadow: none;\n}\n\n#arcane-root.arcane-theme-shadcn .kb-topbar-link.active {\n  font-weight: 600;\n}\n\n#arcane-root.arcane-theme-shadcn .kb-topbar-link::after {\n  content: '';\n  position: absolute;\n  left: 0;\n  right: 0;\n  bottom: -0.7rem;\n  height: 2px;\n  border-radius: 999px;\n  background: transparent;\n}\n\n#arcane-root.arcane-theme-shadcn .kb-topbar-link.active::after {\n  background: var(--foreground);\n}\n\n#arcane-root.arcane-theme-shadcn .arcane-scaffold-sidebar .kb-sidebar {\n  position: relative !important;\n  top: auto !important;\n  width: 100% !important;\n  height: max-content !important;\n  max-height: none !important;\n  min-height: 0 !important;\n  padding: 0.375rem !important;\n  overflow: visible !important;\n}\n\n#arcane-root.arcane-theme-shadcn .arcane-scaffold-sidebar .kb-sidebar-panel {\n  min-height: 0 !important;\n}\n\n/* Docs knowledge-base sidebar (rendered directly under .kb-scaffold, not the\n   arcane scaffold). Pin it directly below the 3.5rem sticky topbar and let it\n   scroll on its own instead of forcing the whole page to scroll. The inline\n   --kb-sidebar-rail-top (56px) otherwise double-counts the topbar height,\n   leaving a gap above the nav. */\n#arcane-root.arcane-theme-shadcn .shadcn-kb-sidebar {\n  top: 3.5rem;\n  max-height: calc(100vh - 3.5rem);\n  overflow-y: auto;\n}\n\n#arcane-root.arcane-theme-shadcn .sidebar-header {\n  margin: 0 0 0.5rem;\n  padding: 0.625rem;\n  border: 1px solid var(--shadcn-hairline);\n  border-radius: var(--radius-lg);\n  background: var(--shadcn-panel-highlight);\n  box-shadow: 0 1px 0 color-mix(in srgb, var(--foreground) 4%, transparent);\n}\n\n#arcane-root.arcane-theme-shadcn .sidebar-nav {\n  padding: 0.375rem 0.125rem 0.625rem !important;\n  gap: 0.375rem !important;\n}\n\n#arcane-root.arcane-theme-shadcn .sidebar-section {\n  margin-bottom: 0.375rem;\n}\n\n#arcane-root.arcane-theme-shadcn .sidebar-section-header {\n  padding: 0.375rem 0.5rem 0.25rem;\n  color: var(--muted-foreground);\n  font-size: 0.6875rem;\n  font-weight: 600;\n  letter-spacing: 0.08em;\n  text-transform: uppercase;\n}\n\n#arcane-root.arcane-theme-shadcn .sidebar-tree {\n  padding-left: 0.75rem;\n  margin-left: 0.25rem;\n  gap: 0.25rem;\n}\n\n#arcane-root.arcane-theme-shadcn .kb-topbar-github,\n#arcane-root.arcane-theme-shadcn .kb-theme-toggle,\n#arcane-root.arcane-theme-shadcn .kb-stylesheet-select,\n#arcane-root.arcane-theme-shadcn .kb-palette-select,\n#arcane-root.arcane-theme-shadcn .kb-hamburger {\n  height: 2.125rem;\n  border: 1px solid var(--shadcn-hairline);\n  background: var(--background);\n  border-radius: var(--radius);\n  box-shadow: none;\n}\n\n#arcane-root.arcane-theme-shadcn .kb-topbar-github,\n#arcane-root.arcane-theme-shadcn .kb-theme-toggle,\n#arcane-root.arcane-theme-shadcn .kb-hamburger {\n  width: 2.125rem;\n}\n\n#arcane-root.arcane-theme-shadcn .kb-topbar .kb-hamburger {\n  display: none !important;\n}\n\n@media (max-width: 900px) {\n  #arcane-root.arcane-theme-shadcn .kb-topbar .kb-hamburger {\n    display: inline-flex !important;\n  }\n}\n\n#arcane-root.arcane-theme-shadcn .kb-topbar-github:hover,\n#arcane-root.arcane-theme-shadcn .kb-theme-toggle:hover,\n#arcane-root.arcane-theme-shadcn .kb-stylesheet-select:hover,\n#arcane-root.arcane-theme-shadcn .kb-palette-select:hover,\n#arcane-root.arcane-theme-shadcn .kb-hamburger:hover {\n  background: var(--shadcn-control-hover);\n  border-color: var(--shadcn-subtle-line);\n}\n\n#arcane-root.arcane-theme-shadcn .kb-search-input,\n#arcane-root.arcane-theme-shadcn .sidebar-search input {\n  height: 2.125rem;\n  border-color: var(--shadcn-hairline);\n  background: var(--background);\n  border-radius: var(--radius);\n}\n\n#arcane-root.arcane-theme-shadcn .kb-search-input:focus,\n#arcane-root.arcane-theme-shadcn .sidebar-search input:focus {\n  border-color: color-mix(in srgb, var(--ring) 42%, transparent);\n  background: var(--background);\n  box-shadow: 0 0 0 2px color-mix(in srgb, var(--ring) 16%, transparent);\n}\n\n#arcane-root.arcane-theme-shadcn .search-results {\n  border-color: var(--shadcn-subtle-line);\n  border-radius: var(--radius-lg);\n  box-shadow: 0 10px 28px -22px rgba(0, 0, 0, 0.42);\n}\n\n#arcane-root.arcane-theme-shadcn .sidebar-tabs {\n  background: var(--shadcn-control-fill);\n  border-radius: var(--radius);\n}\n\n#arcane-root.arcane-theme-shadcn .sidebar-tab {\n  border-radius: calc(var(--radius) - 2px);\n}\n\n#arcane-root.arcane-theme-shadcn .sidebar-tab.active {\n  box-shadow: none;\n}\n\n#arcane-root.arcane-theme-shadcn .sidebar-summary,\n#arcane-root.arcane-theme-shadcn .sidebar-link {\n  border-radius: calc(var(--radius) - 2px);\n  color: color-mix(in srgb, var(--foreground) 78%, var(--muted-foreground));\n  outline: 1px solid transparent;\n  outline-offset: -1px;\n}\n\n#arcane-root.arcane-theme-shadcn .sidebar-summary:hover,\n#arcane-root.arcane-theme-shadcn .sidebar-details[open] > .sidebar-summary,\n#arcane-root.arcane-theme-shadcn .sidebar-link:hover,\n#arcane-root.arcane-theme-shadcn .sidebar-link.active {\n  background: var(--shadcn-control-hover);\n  color: var(--foreground);\n  outline-color: var(--shadcn-subtle-line);\n}\n\n#arcane-root.arcane-theme-shadcn .sidebar-link.active {\n  box-shadow: none;\n}\n\n#arcane-root.arcane-theme-shadcn .sidebar-tree > .sidebar-section::before,\n#arcane-root.arcane-theme-shadcn .sidebar-tree > .sidebar-section::after,\n#arcane-root.arcane-theme-shadcn .sidebar-tree-item::before,\n#arcane-root.arcane-theme-shadcn .sidebar-tree-item::after,\n#arcane-root.arcane-theme-shadcn .sidebar-tree-item:not(:last-child)::after {\n  content: none !important;\n  display: none !important;\n  background: transparent !important;\n}\n\n#arcane-root.arcane-theme-shadcn .toc-content > ul > li::before,\n#arcane-root.arcane-theme-shadcn .toc-content > ul > li::after,\n#arcane-root.arcane-theme-shadcn .toc-content ul ul li::before,\n#arcane-root.arcane-theme-shadcn .toc-content ul ul li::after {\n  background: var(--shadcn-hairline) !important;\n}\n\n#arcane-root.arcane-theme-shadcn .kb-toc-panel .toc {\n  padding: 0.125rem 0 0;\n  border: 0 !important;\n  border-radius: 0 !important;\n  background: transparent !important;\n  box-shadow: none !important;\n}\n\n#arcane-root.arcane-theme-shadcn .kb-toc-panel .toc-title {\n  border-bottom: 0 !important;\n  padding-bottom: 0.125rem;\n  margin-bottom: 0.5rem;\n}\n\n#arcane-root.arcane-theme-shadcn .toc-content a {\n  border-radius: calc(var(--radius) - 2px);\n  background: transparent;\n}\n\n#arcane-root.arcane-theme-shadcn .toc-content a:hover,\n#arcane-root.arcane-theme-shadcn .toc-content a.toc-active {\n  background: var(--shadcn-control-hover);\n}\n\n#arcane-root.arcane-theme-shadcn .kb-main-area {\n  min-width: 0 !important;\n  width: 100% !important;\n  overflow: visible !important;\n}\n\n#arcane-root.arcane-theme-shadcn .kb-content-area {\n  display: grid !important;\n  grid-template-columns: minmax(0, 1fr) !important;\n  align-items: start !important;\n  width: 100% !important;\n  max-width: var(--container-2xl, 90rem) !important;\n  margin: 0 auto !important;\n  gap: clamp(1.75rem, 3vw, 3rem) !important;\n  padding: clamp(1.75rem, 3vw, 3rem) clamp(1.5rem, 4vw, 3.5rem) !important;\n}\n\n/* Reserve the right-hand TOC column only when a table of contents is actually\n   present (prose pages with headings). TOC-less pages (e.g. component docs)\n   render a single centered column instead of leaving an empty 17rem gap. */\n@media (min-width: 1201px) {\n  #arcane-root.arcane-theme-shadcn .kb-content-area:has(.kb-toc-panel) {\n    grid-template-columns: minmax(0, 1fr) minmax(12rem, 17rem) !important;\n  }\n}\n\n#arcane-root.arcane-theme-shadcn .kb-article-panel {\n  min-width: 0 !important;\n  width: 100% !important;\n  max-width: 68rem !important;\n  margin-left: auto !important;\n  margin-right: auto !important;\n}\n\n#arcane-root.arcane-theme-shadcn .kb-page-metadata,\n#arcane-root.arcane-theme-shadcn .kb-tags-footer,\n#arcane-root.arcane-theme-shadcn .prose h2,\n#arcane-root.arcane-theme-shadcn .prose hr,\n#arcane-root.arcane-theme-shadcn .prose blockquote,\n#arcane-root.arcane-theme-shadcn .prose th,\n#arcane-root.arcane-theme-shadcn .prose td,\n#arcane-root.arcane-theme-shadcn .prose pre {\n  border-color: var(--shadcn-hairline) !important;\n}\n\n#arcane-root.arcane-theme-shadcn .prose th {\n  background: var(--shadcn-control-fill);\n}\n\n#arcane-root.arcane-theme-shadcn .arcane-demo-preview-scope,\n#arcane-root.arcane-theme-shadcn .arcane-demo-code {\n  border-color: var(--shadcn-subtle-line) !important;\n  border-radius: var(--radius-lg);\n  box-shadow: none !important;\n}\n\n#arcane-root.arcane-theme-shadcn .arcane-demo-panel {\n  padding: 1.5rem !important;\n  border: 1px solid var(--shadcn-subtle-line) !important;\n  border-radius: var(--radius-xl) !important;\n  background: color-mix(in srgb, var(--card) 96%, var(--background)) !important;\n  box-shadow: none !important;\n}\n\n#arcane-root.arcane-theme-shadcn .arcane-demo-kicker,\n#arcane-root.arcane-theme-shadcn .arcane-demo-code-label {\n  color: var(--muted-foreground) !important;\n}\n\n#arcane-root.arcane-theme-shadcn .arcane-demo-component-chip {\n  border: 1px solid transparent !important;\n  border-radius: 999px !important;\n  background: var(--shadcn-control-fill) !important;\n  color: var(--muted-foreground) !important;\n  box-shadow: none !important;\n}\n\n#arcane-root.arcane-theme-shadcn .arcane-demo-section-title {\n  color: var(--foreground) !important;\n}\n\n#arcane-root.arcane-theme-shadcn .arcane-demo-preview-scope {\n  display: flex !important;\n  align-items: center !important;\n  justify-content: center !important;\n  border-width: 1px !important;\n  background: color-mix(in srgb, var(--card) 96%, var(--background)) !important;\n}\n\n#arcane-root.arcane-theme-shadcn .arcane-demo-preview-scope > .arcane-box {\n  display: flex !important;\n  align-items: center !important;\n  justify-content: center !important;\n}\n\n#arcane-root.arcane-theme-shadcn .arcane-demo-missing {\n  border: 1px solid var(--shadcn-subtle-line) !important;\n  border-radius: var(--radius-lg) !important;\n  background: var(--background) !important;\n  color: var(--foreground) !important;\n  box-shadow: none !important;\n}\n\n#arcane-root.arcane-theme-shadcn .arcane-demo-missing-icon {\n  border: 1px solid color-mix(in srgb, var(--warning, #f59e0b) 42%, var(--border)) !important;\n  border-radius: var(--radius-sm) !important;\n  background: color-mix(in srgb, var(--warning, #f59e0b) 16%, var(--background)) !important;\n  color: color-mix(in srgb, var(--warning, #f59e0b) 74%, var(--foreground)) !important;\n}\n\n#arcane-root.arcane-theme-shadcn .arcane-demo-missing-title {\n  color: var(--foreground) !important;\n}\n\n#arcane-root.arcane-theme-shadcn .arcane-demo-missing-body {\n  color: var(--muted-foreground) !important;\n}\n\n@media (max-width: 900px) {\n  #arcane-root.arcane-theme-shadcn .arcane-scaffold-sidebar.arcane-scaffold-sidebar {\n    position: static !important;\n    top: auto !important;\n    height: auto !important;\n    max-height: none !important;\n    min-height: 0 !important;\n    overflow: visible !important;\n  }\n}\n\n\n#arcane-root.arcane-theme-shadcn .kb-topbar::before,\n#arcane-root.arcane-theme-shadcn .kb-topbar::after {\n  content: none !important;\n  display: none !important;\n  box-shadow: none !important;\n}\n\n#arcane-root.arcane-theme-shadcn .kb-toc-panel {\n  position: sticky !important;\n  top: 5rem !important;\n  align-self: flex-start !important;\n  width: 100% !important;\n  max-height: none !important;\n  overflow: visible !important;\n}\n\n@media (max-width: 1200px) {\n  #arcane-root.arcane-theme-shadcn .kb-content-area {\n    grid-template-columns: minmax(0, 1fr) !important;\n  }\n\n  #arcane-root.arcane-theme-shadcn .kb-toc-panel {\n    display: none !important;\n  }\n}\n\n@media (max-width: 900px) {\n  #arcane-root.arcane-theme-shadcn .arcane-scaffold-body {\n    grid-template-columns: minmax(0, 1fr) !important;\n  }\n\n  #arcane-root.arcane-theme-shadcn .arcane-scaffold-sidebar.arcane-scaffold-sidebar {\n    position: static !important;\n    width: auto !important;\n  }\n\n  #arcane-root.arcane-theme-shadcn .kb-content-area {\n    padding: 1.25rem !important;\n  }\n}\n\n\n#arcane-root.arcane-theme-shadcn .kb-landing-hero {\n  background:\n    linear-gradient(135deg, color-mix(in srgb, var(--card) 92%, var(--primary) 8%), color-mix(in srgb, var(--background) 88%, var(--primary) 12%)),\n    linear-gradient(90deg, color-mix(in srgb, var(--primary) 7%, transparent) 1px, transparent 1px),\n    linear-gradient(color-mix(in srgb, var(--border) 54%, transparent) 1px, transparent 1px);\n  background-size: auto, 4rem 4rem, 4rem 4rem;\n}\n\n#arcane-root.arcane-theme-shadcn .kb-landing-prose {\n  display: grid;\n  gap: clamp(1.5rem, 2.4vw, 2.4rem);\n}\n\n#arcane-root.arcane-theme-shadcn .kb-landing-prose > * + * {\n  margin-top: 0;\n}\n\n#arcane-root.arcane-theme-shadcn .kb-landing-grid {\n  gap: clamp(1.25rem, 2vw, 1.8rem);\n  margin-top: 1.25rem;\n  margin-bottom: 1.5rem;\n}\n\n#arcane-root.arcane-theme-shadcn .kb-landing-band {\n  gap: clamp(1.45rem, 2.4vw, 2.2rem);\n  margin-top: 1.25rem;\n  padding: clamp(1.5rem, 2.4vw, 2.25rem);\n}\n\n#arcane-root.arcane-theme-shadcn .kb-landing-terminal-body,\n#arcane-root.arcane-theme-shadcn .kb-landing-list {\n  gap: 1rem;\n}\n\n#arcane-root.arcane-theme-shadcn .kb-landing-card:hover {\n  border-color: color-mix(in srgb, var(--primary) 42%, var(--border));\n  box-shadow: 0 1rem 2.5rem color-mix(in srgb, var(--primary) 10%, transparent);\n}\n\n\n",new A.lx(4294440172,4278519045,4279308561,4279900698,i,4284881692,4281236821,4288700450,4278221516,!0,!1,i),"",B.cO,g,B.co)
if(m.length!==0)B.b.m(s,new A.cc("style",i,m))
j.a.toString
B.b.m(s,new A.cc("style",i,'[data-arcane-surface][hidden] { display: none !important; }\n[data-arcane-overlay-open] { overflow: hidden; }\n[data-arcane-toast-surface] {\n  position: fixed;\n  z-index: 99999;\n  bottom: 1rem;\n  right: 1rem;\n  display: flex;\n  flex-direction: column;\n  gap: 0.5rem;\n  pointer-events: none;\n}\n[data-arcane-toast-surface] .arcane-toast { pointer-events: auto; }\n[data-arcane-tab-panel][hidden] { display: none !important; }\n[data-arcane-panel-content][hidden] { display: none !important; }\n[data-arcane-command-item][hidden] { display: none !important; }\n[data-arcane-command-group][hidden] { display: none !important; }\n[data-arcane-command-empty][hidden] { display: none !important; }\n[data-arcane-command-item][data-arcane-state="active"] {\n  background-color: var(--accent, rgba(127,127,127,0.1));\n}\n[data-arcane-calendar] {\n  display: flex;\n  flex-direction: column;\n  gap: 0.5rem;\n  width: min(100%, 20rem);\n  min-width: min(18rem, 100%);\n  box-sizing: border-box;\n}\n[data-arcane-calendar] .arcane-calendar-header {\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  gap: 0.5rem;\n}\n[data-arcane-calendar] .arcane-calendar-label {\n  flex: 1;\n  text-align: center;\n  font-weight: 500;\n}\n[data-arcane-calendar] .arcane-calendar-nav,\n[data-arcane-calendar] .arcane-calendar-today {\n  background: transparent;\n  border: 1px solid var(--border, rgba(127,127,127,0.2));\n  border-radius: 0.25rem;\n  padding: 0.25rem 0.5rem;\n  cursor: pointer;\n  font: inherit;\n  color: inherit;\n}\n[data-arcane-calendar] .arcane-calendar-weekdays {\n  display: grid;\n  grid-template-columns: repeat(7, 1fr);\n  gap: 0.25rem;\n  font-size: 0.75rem;\n  text-transform: uppercase;\n  opacity: 0.7;\n  text-align: center;\n}\n[data-arcane-calendar][data-arcane-show-week-numbers="true"] .arcane-calendar-weekdays {\n  grid-template-columns: 2rem repeat(7, 1fr);\n}\n[data-arcane-calendar] .arcane-calendar-grid {\n  display: grid;\n  grid-template-columns: repeat(7, 1fr);\n  gap: 0.25rem;\n}\n[data-arcane-calendar][data-arcane-show-week-numbers="true"] .arcane-calendar-grid {\n  grid-template-columns: 2rem repeat(7, 1fr);\n}\n[data-arcane-calendar] .arcane-calendar-weeknum {\n  text-align: center;\n  font-size: 0.75rem;\n  opacity: 0.5;\n  padding: 0.25rem 0;\n}\n[data-arcane-calendar] .arcane-calendar-day {\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  width: 100%;\n  min-width: 2.25rem;\n  min-height: 2.25rem;\n  aspect-ratio: 1 / 1;\n  background: transparent;\n  border: 1px solid transparent;\n  border-radius: 0.25rem;\n  padding: 0;\n  cursor: pointer;\n  font: inherit;\n  color: inherit;\n  text-align: center;\n  box-sizing: border-box;\n}\n[data-arcane-calendar] .arcane-calendar-day:hover:not(:disabled) {\n  background: var(--muted, rgba(127,127,127,0.1));\n}\n[data-arcane-calendar] .arcane-calendar-day-other-month { opacity: 0.4; }\n[data-arcane-calendar] .arcane-calendar-day-disabled { opacity: 0.3; cursor: not-allowed; }\n[data-arcane-calendar] .arcane-calendar-day-today {\n  border-color: var(--primary, currentColor);\n}\n[data-arcane-calendar] .arcane-calendar-day-selected,\n[data-arcane-calendar] .arcane-calendar-day-pending,\n[data-arcane-calendar] .arcane-calendar-day-range-start,\n[data-arcane-calendar] .arcane-calendar-day-range-end {\n  background: var(--primary, currentColor);\n  color: var(--primary-foreground, white);\n}\n[data-arcane-calendar] .arcane-calendar-day-in-range:not(.arcane-calendar-day-range-start):not(.arcane-calendar-day-range-end) {\n  background: var(--accent, rgba(127,127,127,0.2));\n}\n'))
l=f===B.aB?"dark":"light"
k=new A.a3(A.a([l,"arcane-theme-shadcn","shadcn-midnight"],t.mf),t.i7.a(new A.u_()),t.cA).aA(0," ")
g=A.j(["data-arcane-theme","shadcn"],r,r)
f=t.i
q=A.a([j.a.f],f)
j.a.toString
q.push(B.bN)
n=j.a.e
f=A.a([A.DL(A.j(["class",l],r,r)),A.DK(s)],f)
r=j.a
f.push(new A.hh(i,r.x,i))
f.push(new A.c("arcane-root",k,B.lm,g,i,q,i))
return new A.fO(e,n,new A.bK(f,i),i)}}
A.u_.prototype={
$1(a){A.aA(a)
return a!=null&&a.length!==0},
$S:111}
A.cc.prototype={}
A.oK.prototype={
$1(a){var s
A.p(a)
s=A.a7(A.p(v.G.document).getElementById("loading"))
if(s!=null)A.p(s.classList).add("hidden")},
$S:7}
A.fe.prototype={
l(a){var s=this,r=t.N,q=A.t(r,r)
r=s.e
if(r!=null)q.i(0,"font-size",r.gaT())
r=s.f
if(r!=null)q.i(0,"font-weight",""+r.gfh())
r=s.r
if(r!=null)q.i(0,"color",r.gaT())
r=s.y
if(r!=null)q.i(0,"line-height",r.gaT())
return s.jj(q)},
jj(a){var s,r=null,q="arcane-text"
t.f.a(a)
s=A.a([new A.k(this.d,r)],t.i)
switch(this.db){case"h1":return A.CD(s,q,A.B(a))
case"h2":return new A.n5(q,A.B(a),s,r)
case"h3":return new A.n6(q,A.B(a),s,r)
case"h4":return new A.n7(q,A.B(a),s,r)
case"h5":return new A.n8(q,A.B(a),s,r)
case"h6":return new A.n9(q,A.B(a),s,r)
case"p":return A.zq(s,q,A.B(a))
case"code":return new A.n0(q,A.B(a),s,r)
case"pre":return new A.nj(q,A.B(a),s,r)
case"strong":return new A.nn(q,A.B(a),s,r)
case"em":return new A.n2(q,A.B(a),s,r)
case"small":return new A.nm(q,A.B(a),s,r)
default:return A.H(s,r,q,r,A.B(a))}}}
A.j4.prototype={
gkO(){switch(0){case 0:break}return B.cB},
gkN(){switch(1){case 1:break}return B.cA},
l(a){var s=this
A.b5(a)
return new A.l_(new A.oU(s.d,s.e,s.f,null,null,s.gkO(),s.gkN()),null)}}
A.ev.prototype={
U(){return new A.i1()}}
A.i1.prototype={
gcT(){this.a.toString
var s=this.d
return s},
c2(){var s=this,r=s.e
if(r!=null)r.W()
s.e=null
r=s.f
if(r!=null)r.W()
s.f=null},
lv(){var s,r=this
r.c2()
s=r.gcT()
r.a.toString
r.t(new A.u2(r,!s))
r.a.toString},
kB(){var s=this
if(!s.gcT()){s.a.toString
s.t(new A.u1(s))
s.a.toString}},
jC(){var s=this
if(s.gcT()){s.a.toString
s.t(new A.u0(s))
s.a.toString}},
kb(){var s=this
s.c2()
s.c2()
s.a.toString
s.kB()},
kd(){var s=this
s.c2()
s.c2()
s.a.toString
s.jC()},
l(a){var s,r,q,p,o,n,m=this
m.a.toString
A.b5(a)
s=m.a
r=s.e
q=s.f
p=s.r
o=m.gcT()
n=s.x
s=s.ay
return new A.l0(new A.ph(null,r,q,p,o,B.ac,n,m.glu(),m.gka(),m.gkc(),!1,8,s,!0,!0),null)}}
A.u2.prototype={
$0(){return this.a.d=this.b},
$S:0}
A.u1.prototype={
$0(){return this.a.d=!0},
$S:0}
A.u0.prototype={
$0(){return this.a.d=!1},
$S:0}
A.cd.prototype={
E(){return"IconSize."+this.b}}
A.a6.prototype={
l(a){var s=""+A.Ec(this.e)+"px",r=t.N
return new A.na(A.B(A.j(["font-family","'lucide', sans-serif","font-style","normal","font-weight","normal","font-size",s,"line-height","1","display","inline-block","width",s,"height",s,"-webkit-font-smoothing","antialiased","-moz-osx-font-smoothing","grayscale"],r,r)),A.a([new A.k(A.am(A.CE(this.d,16)),null)],t.i),null)}}
A.fN.prototype={}
A.ih.prototype={
mh(){return"value."+this.b+" "+A.BZ(this.a)+" "+A.BZ(this.c)}}
A.dE.prototype={}
A.nu.prototype={
E(){return"AccordionVariant."+this.b}}
A.nt.prototype={}
A.dJ.prototype={
E(){return"ButtonVariant."+this.b}}
A.jo.prototype={
E(){return"ButtonSize."+this.b}}
A.fV.prototype={}
A.jr.prototype={
E(){return"CardVariant."+this.b}}
A.od.prototype={}
A.oe.prototype={}
A.or.prototype={}
A.oJ.prototype={}
A.hP.prototype={
E(){return"SheetPosition."+this.b}}
A.hQ.prototype={
E(){return"SheetSizeVariant."+this.b}}
A.rR.prototype={}
A.jJ.prototype={
E(){return"EmptyStateStyleVariant."+this.b}}
A.oV.prototype={
E(){return"EmptyStateSizeVariant."+this.b}}
A.oU.prototype={}
A.jU.prototype={
E(){return"FloatingTrigger."+this.b}}
A.pg.prototype={
E(){return"FloatingPosition."+this.b}}
A.ph.prototype={}
A.cg.prototype={}
A.qz.prototype={}
A.nN.prototype={}
A.rz.prototype={
E(){return"ScrollDirectionVariant."+this.b}}
A.rB.prototype={
E(){return"ScrollbarVisibilityVariant."+this.b}}
A.rA.prototype={
E(){return"ScrollbarStyleVariant."+this.b}}
A.ry.prototype={}
A.rT.prototype={}
A.bv.prototype={
E(){return"BadgeVariant."+this.b}}
A.rY.prototype={
gmg(){if(this.c!==B.o)return this.r
return this.r}}
A.cl.prototype={
E(){return"StatusType."+this.b}}
A.lw.prototype={
E(){return"TextInputType."+this.b}}
A.t4.prototype={}
A.ff.prototype={
E(){return"ToastVariant."+this.b}}
A.ta.prototype={
E(){return"ToastPosition."+this.b}}
A.e6.prototype={}
A.t9.prototype={}
A.tb.prototype={}
A.jn.prototype={
l(a){var s,r,q,p=this,o=p.c,n=o.x,m=n?"not-allowed":"pointer",l=n?"none":"auto",k=n?"0.5":"1",j=t.N
k=A.ce(A.j(["display","inline-flex","align-items","center","justify-content","center","gap","var(--space-2)","white-space","nowrap","border-radius","var(--radius)","font-size","var(--font-size-sm)","font-weight","var(--font-weight-medium)","line-height","1.25rem","transition","color var(--transition), background-color var(--transition), border-color var(--transition), box-shadow var(--transition)","outline","none","cursor",m,"pointer-events",l,"opacity",k,"user-select","none","-webkit-user-select","none"],j,j),j,j)
l=o.r
k.B(0,p.fi(l))
m=o.w
k.B(0,p.iE(m))
if(o.z)k.i(0,"width","100%")
s=A.a([],t.i)
B.b.m(s,new A.k(o.a,null))
r=A.t(j,j)
r.i(0,"data-state","idle")
r.i(0,"data-disabled",""+n)
r.i(0,"data-variant",l.b)
r.i(0,"data-size",m.b)
q=A.zm(o.f)
m=A.t(j,j)
if(n)m.i(0,"disabled","true")
m.i(0,"type","button")
m.B(0,r)
m.B(0,q)
l=A.B(k)
return A.fE(s,m,"arcane-button",A.j(["click",new A.o5(p,n)],j,t.v),o.Q,l,null)}}
A.o5.prototype={
$1(a){A.p(a)
if(!this.b&&this.a.c.e!=null)this.a.c.e.$0()},
$S:4}
A.jq.prototype={
l(a){var s=this.c,r=A.a([s.a],t.i),q=t.N,p=A.t(q,q),o=s.e
p.i(0,"border-radius",o==null?"0.5rem":o)
p.i(0,"color","var(--card-foreground)")
o=s.d
if(o!=null)p.i(0,"padding",o)
else p.i(0,"padding","1.5rem")
p.i(0,"width","100%")
p=A.ce(p,q,q)
p.B(0,this.fi(s))
s=A.j(["data-variant",s.c.b],q,q)
return new A.c(null,"arcane-card",A.B(p),s,null,r,null)}}
A.js.prototype={
l(a){var s,r,q,p=null,o="unchecked",n=this.c,m=n.a,l=n.b,k=A.CB(p,!1,m,"multi",l?"on":""),j=A.CC(!1,m,l,"on"),i=l?"checked":o,h=t.N,g=t.gm
i=A.a([j,A.j(["role","checkbox","aria-checked",""+l,"tabindex","0","data-state",i,"data-disabled","false"],h,h)],g)
i.push(A.zm(new A.ih(m,"toggle","on")))
s=A.nf(i)
j=A.t(h,h)
j.i(0,"data-state",l?"checked":o)
j.i(0,"data-disabled","false")
j.B(0,B.x)
r=A.nf(A.a([k,j],g))
l=A.B(A.j(["display","flex","align-items","flex-start","gap","var(--space-2)","cursor","pointer","opacity","1","pointer-events","auto"],h,h))
j=n.w==null?p:A.j(["click",new A.of(this)],h,t.v)
i=t.i
g=A.a([this.lV(n,s)],i)
q=A.a([],i)
h=A.B(A.j(["font-size","var(--font-size-sm)","font-weight","var(--font-weight-medium)","color","var(--foreground)","display","block","line-height","1"],h,h))
q.push(A.H(A.a([new A.k(n.c,p)],i),p,p,p,h))
g.push(new A.c(p,p,B.bz,p,p,q,p))
return new A.c(p,"arcane-checkbox-wrapper",l,r,j,g,p)}}
A.of.prototype={
$1(a){var s
A.p(a)
s=this.a.c
return s.w.$1(!s.b)},
$S:4}
A.jD.prototype={
l(a){var s=null,r=this.c,q=r.f,p=t.N,o=A.B(A.j(["display","flex","flex-direction","column","align-items","center","text-align","center","gap","var(--space-4)"],p,p)),n=t.i,m=A.a([],n)
m.push(new A.c(s,s,A.B(A.j(["color","var(--foreground)","font-size","var(--font-size-sm)","line-height","1.625"],p,p)),s,s,A.a([new A.k(r.b,s)],n),s))
p=A.a([new A.c(s,"arcane-confirm-dialog-content",o,s,s,m,s)],n)
return new A.kZ(new A.oJ(r.a,p,A.a([new A.f9(new A.fV(r.d,s,s,s,q,s,B.aD,B.v,!1,!1,!1,s,s,s,!1),s),new A.f9(new A.fV(r.c,s,s,s,r.e,s,B.aE,B.v,!1,!1,!1,s,s,s,!1),s)],n),q,400),s)}}
A.jI.prototype={
l(a){var s,r,q,p=this,o=null,n=t.i,m=A.a([],n),l=t.N,k=p.c
m.push(new A.c(o,"arcane-empty-state-icon",A.B(A.j(["font-size",p.geA().a[0],"line-height","1","opacity","0.6"],l,l)),o,o,A.a([k.a],n),o))
s=p.geA().a
r=s[1]
q=s[2]
s=A.a([new A.c(o,"arcane-empty-state-title",A.B(A.j(["font-size",r,"font-weight","var(--font-weight-semibold)","color","var(--foreground)"],l,l)),o,o,A.a([new A.k(k.b,o)],n),o)],n)
s.push(new A.c(o,"arcane-empty-state-description",A.B(A.j(["font-size",q,"color","var(--muted-foreground)","max-width","360px"],l,l)),o,o,A.a([new A.k(k.c,o)],n),o))
B.b.B(m,s)
n=p.geA().a
return p.lW(new A.c(o,"arcane-empty-state",A.B(A.j(["display","flex","flex-direction","column","align-items","center","justify-content","center","text-align","center","padding",n[3],"gap",n[4]],l,l)),o,o,m,o))}}
A.jT.prototype={
l(a){var s,r,q,p,o=null,n=this.c,m=n.d,l=m==null
if(!l&&n.f===B.ac){s=l?"":m
r=n.r
q=t.N
s=A.j(["data-tooltip",s,"data-tooltip-position",r.b],q,q)
r=t.f.a(this.k0(r))
q=A.t(q,q)
q.i(0,"position","absolute")
q.i(0,"z-index","50")
q.i(0,"padding","6px 12px")
p=n.ay
q.i(0,"max-width",""+(p==null?250:p)+"px")
q.i(0,"font-size","var(--font-size-sm)")
q.i(0,"font-weight","var(--font-weight-medium)")
q.i(0,"line-height","1.4")
q.i(0,"color","var(--popover-foreground)")
q.i(0,"background-color","var(--popover)")
q.i(0,"border","1px solid var(--border)")
q.i(0,"border-radius","var(--radius-sm)")
q.i(0,"box-shadow","var(--shadow-md)")
q.i(0,"overflow","hidden")
q.i(0,"white-space","nowrap")
q.i(0,"pointer-events","none")
q.i(0,"opacity","0")
q.i(0,"visibility","hidden")
q.i(0,"transition","opacity var(--transition), visibility var(--transition), transform var(--transition)")
q.B(0,r)
r=A.B(q)
if(l)m=""
l=t.i
return new A.c(o,"arcane-floating-trigger",B.lz,s,o,A.a([n.b,new A.c(o,"arcane-floating arcane-floating-tooltip",r,B.eh,o,A.a([new A.k(m,o)],l),o)],l),o)}return this.jo()},
jo(){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e,d,c,b,a,a0=this,a1=null,a2=a0.c,a3=a2.f,a4=a3===B.ac,a5=a3===B.cN
a3=$.B3+1
$.B3=a3
s="arcane-floating-"+a3
r=s+"-trigger"
q=a4?"hovercard":"popover"
a3=t.N
p=A.t(a3,a3)
p.i(0,"data-arcane-anchor-id",r)
if(a5)p.i(0,"data-arcane-action","surface.toggle:"+s)
if(a4)p.B(0,A.j(["data-arcane-mouseenter","surface.hoverOpen:"+s,"data-arcane-mouseleave","surface.hoverClose:"+s],a3,a3))
o=t.v
n=A.t(a3,o)
if(a4)n.i(0,"mouseenter",new A.pi(a0))
if(a4)n.i(0,"mouseleave",new A.pj(a0))
o=A.t(a3,o)
if(a5)o.i(0,"click",new A.pk(a0))
m=t.i
l=A.a([a2.b],m)
k=a0.k8()
j=k.a
i=a1
h=a1
g=k.b
f=k.c
h=f
i=g
e=j
d=a2.ay
c=A.zs(r,B.c.k(a2.as),a2.r.b,!0,!0,!1,s,a2.e,!0,!1,q)
b=A.t(a3,a3)
b.i(0,"role",q==="hovercard"?"tooltip":"dialog")
b.B(0,c)
t.f.a(h)
A.r(e)
A.r(i)
a3=A.t(a3,a3)
a3.i(0,"position","absolute")
a3.i(0,e,i)
a3.B(0,h)
a3.i(0,"z-index","50")
if(d!=null)a3.i(0,"max-width",A.w(d)+"px")
a3.i(0,"background-color","var(--popover)")
a3.i(0,"border","1px solid var(--border)")
a3.i(0,"border-radius","var(--radius-sm)")
a3.i(0,"box-shadow","var(--shadow-md)")
a3.i(0,"padding","6px 12px")
a3.i(0,"color","var(--popover-foreground)")
a3.i(0,"outline","none")
a3=A.B(a3)
a=A.a([],m)
a2=a2.d
if(a2!=null)a.push(new A.k(a2,a1))
return new A.c(a1,"arcane-floating-container",B.la,a1,n,A.a([new A.c(a1,a1,B.ly,p,o,l,a1),new A.c(a1,"arcane-floating-content",a3,b,a1,a,a1)],m),a1)},
k0(a){var s
switch(a.a){case 0:s=B.dT
break
case 1:s=B.dQ
break
case 2:s=B.ek
break
case 3:s=B.ew
break
case 4:s=B.eS
break
case 5:s=B.fO
break
case 6:s=B.eY
break
case 7:s=B.fi
break
default:s=null}return s},
k8(){var s="bottom",r=this.c,q=""+r.as+"px"
switch(r.r.a){case 0:r=new A.bQ(s,"calc(100% + "+q+")",B.b3)
break
case 1:r=new A.bQ("top","calc(100% + "+q+")",B.b3)
break
case 2:r=new A.bQ("right","calc(100% + "+q+")",B.b_)
break
case 3:r=new A.bQ("left","calc(100% + "+q+")",B.b_)
break
case 4:r=new A.bQ(s,"calc(100% + "+q+")",B.b0)
break
case 5:r=new A.bQ(s,"calc(100% + "+q+")",B.b2)
break
case 6:r=new A.bQ("top","calc(100% + "+q+")",B.b0)
break
case 7:r=new A.bQ("top","calc(100% + "+q+")",B.b2)
break
default:r=null}return r}}
A.pi.prototype={
$1(a){var s
A.p(a)
s=this.a.c.y.$0()
return s},
$S:4}
A.pj.prototype={
$1(a){var s
A.p(a)
s=this.a.c.z.$0()
return s},
$S:4}
A.pk.prototype={
$1(a){var s
A.p(a)
s=this.a.c.x.$0()
return s},
$S:4}
A.kr.prototype={
l(a){var s=this.c,r=s.a,q=A.F(r),p=q.h("E<1,ah>"),o=A.x(new A.E(r,q.h("ah(1)").a(new A.qA()),p),p.h("z.E"))
return A.yq(!1,s.y,!1,s.w,s.x,s.r,s.Q,o,s.c,!1,s.d,s.b)}}
A.qA.prototype={
$1(a){t.i_.a(a)
return new A.ah(a.a,a.b,!1)},
$S:108}
A.kU.prototype={
l(a){var s,r,q,p,o,n,m,l=null,k=this.c
switch(k.f.a){case 0:s=B.jg
break
case 1:s=B.jc
break
case 2:s=B.jb
break
default:s=l}r=s.a
q=l
p=s.b
q=p
o=r
switch(k.r.a){case 0:s=t.N
s=A.t(s,s)
break
case 1:s=t.N
s=A.j(["scrollbar-width","none"],s,s)
break
case 2:s=t.N
s=A.t(s,s)
break
case 3:s=t.N
s=A.j(["scrollbar-width","none","-ms-overflow-style","none"],s,s)
break
default:s=l}n=t.N
m=A.t(n,n)
m.i(0,"position","relative")
m.i(0,"height",k.b)
m.i(0,"overflow-x",o)
m.i(0,"overflow-y",q)
m.B(0,s)
m.i(0,"scroll-behavior","smooth")
m.i(0,"-webkit-overflow-scrolling","touch")
m.B(0,A.j(["scrollbar-color","var(--border) transparent"],n,n))
s=A.B(m)
n=A.a([],t.i)
n.push(k.a)
return new A.c(l,"arcane-scroll-area "+("arcane-scroll-area-"+k.ax)+" ",s,l,l,n,l)}}
A.lk.prototype={
dR(a){var s,r=a.d
A:{if(B.T===r||B.jR===r){s="var(--success)"
break A}if(B.U===r||B.jT===r){s="var(--warning)"
break A}if(B.am===r||B.jS===r){s="var(--destructive)"
break A}if(B.a2===r){s="var(--info)"
break A}if(B.bu===r){s="var(--muted-foreground)"
break A}s=null}return s},
iI(a){var s
switch(a.b.a){case 0:s="0.75rem"
break
case 1:s="0.875rem"
break
case 2:s="1rem"
break
default:s=null}return s},
l(a4){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e,d=this,c=null,b="color-mix(in srgb, ",a="shadcn-status-indicator",a0="shadcn-status-label",a1=d.c,a2=a1.z,a3=a1.c
if(a3===B.a7||a3===B.a8||a3===B.a9){s=b+d.f4(a1)
r=t.N
s=A.ce(A.j(["display","inline-flex","align-items","center","gap","var(--space-2)","padding",d.hk(a1),"background",s+" 15%, transparent)","border","1px solid "+(s+" 35%, transparent)"),"border-radius","9999px"],r,r),r,r)
d.fw(s,a2)
q=d.f4(a1)
s=A.B(s)
p=d.f4(a1)
o=d.hL(a1)
r=A.H(B.n,c,a,c,A.B(A.j(["width",o,"height",o,"border-radius","50%","background",p,"flex-shrink","0","box-shadow","0 0 8px "+p],r,r)))
n=A.B(d.hS(a1,q))
m=t.i
return new A.c(c,"shadcn-status-badge shadcn-promo-badge shadcn-badge-"+a3.b,s,c,c,A.a([r,A.H(A.a([new A.k(a1.a,c)],m),c,a0,c,n)],m),c)}l=a3===B.aw||a3===B.aa||a3===B.ax||a3===B.ay||a3===B.az||a3===B.aA||a3===B.av
if(l){s=t.N
s=A.ce(A.j(["display","inline-flex","align-items","center","gap","0.375rem","border-radius","9999px","font-size",d.jv(a1),"font-weight","600","line-height","1","white-space","nowrap","transition","color 150ms, background-color 150ms, border-color 150ms","padding",d.jw(a1)],s,s),s,s)
d.fw(s,a2)
t.f.a(s)
r=d.ju(a1).a
k=r[0]
j=c
i=c
h=c
g=r[1]
f=r[2]
e=r[3]
h=e
i=f
j=g
s.i(0,"background-color",k)
s.i(0,"color",j)
s.i(0,"border",h==null?"1px solid transparent":h)
if(i!=null)s.i(0,"box-shadow",i)
s=A.B(s)
r=A.a([],t.i)
r.push(new A.k(a1.a,c))
return A.H(r,c,"shadcn-badge shadcn-badge-"+a3.b,c,s)}q=d.dR(a1)
a3=b+d.dR(a1)
s=t.N
a3=A.B(A.j(["display","inline-flex","align-items","center","gap","var(--space-2)","padding",d.hk(a1),"background",a3+" 10%, transparent)","border","1px solid "+(a3+" 25%, transparent)"),"border-radius","9999px"],s,s))
r=t.i
n=A.a([],r)
if(a1.gmg()){p=d.dR(a1)
o=d.hL(a1)
s=A.t(s,s)
s.i(0,"width",o)
s.i(0,"height",o)
s.i(0,"border-radius","50%")
s.i(0,"background",p)
s.i(0,"flex-shrink","0")
if(a1.f)s.i(0,"animation","arcane-pulse 2s ease-in-out infinite")
n.push(A.H(B.n,c,a,c,A.B(s)))}s=A.B(d.hS(a1,q))
n.push(A.H(A.a([new A.k(a1.a,c)],r),c,a0,c,s))
return new A.c(c,"shadcn-status-badge shadcn-status-"+a1.d.b,a3,c,c,n,c)},
fw(a,b){t.f.a(a)
return}}
A.lv.prototype={
l(a5){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e=null,d="width",c="100%",b=this.c,a=b.at,a0=a==null,a1=!a0,a2=b.Q,a3=a2==null,a4=!0
if(a3)if(a0){a0=b.as!=null
a4=a0}a0=this.iF(b.f).a
s=a0[0]
r=e
q=e
p=e
o=a0[1]
n=a0[2]
m=a0[3]
p=m
q=n
r=o
switch(b.b.a){case 0:a0=B.d1
break
case 1:a0=B.cX
break
case 2:a0=B.cZ
break
case 3:a0=B.cY
break
case 4:a0=B.d0
break
case 5:a0=B.d2
break
case 6:a0=B.d_
break
default:a0=e}l=t.N
k=A.t(l,l)
j=b.a
if(j!=null)k.i(0,"placeholder",j)
k.i(0,"value",b.c)
j=b.w
if(j)k.i(0,"disabled","true")
i=""+j
k.i(0,"data-disabled",i)
h=""+a1
k.i(0,"data-error",h)
k.B(0,A.t(l,l))
A.r(p)
A.r(s)
A.r(r)
A.r(q)
g=A.t(l,l)
g.i(0,"display","flex")
g.i(0,"height",s)
g.i(0,d,c)
g.i(0,"border-radius","var(--radius-sm)")
g.i(0,"border",a1?"1px solid var(--destructive)":"1px solid var(--input)")
g.i(0,"background-color","var(--background)")
g.i(0,"padding",q+" "+r)
g.i(0,"font-size",p)
g.i(0,"font-family","inherit")
g.i(0,"line-height","1.5")
g.i(0,"color","var(--foreground)")
g.i(0,"outline","none")
if(j)g.i(0,"cursor","not-allowed")
if(j)g.i(0,"opacity","0.5")
g.i(0,"transition","border-color var(--transition), box-shadow var(--transition)")
j=A.B(g)
f=A.HY(k,"arcane-text-input",this.kg(),b.e,b.d,j,a0,t.z)
if(!a4){a=A.t(l,l)
if(b.z)a.i(0,d,c)
return new A.c(e,e,A.B(a),e,e,A.a([f],t.i),e)}a0=A.j(["data-disabled",i,"data-error",h],l,l)
l=A.t(l,l)
l.i(0,"display","flex")
l.i(0,"flex-direction","column")
l.i(0,"gap","var(--space-2)")
if(b.z)l.i(0,d,c)
l=A.B(l)
k=t.i
j=A.a([],k)
if(!a3){a2=A.a([new A.k(a2,e)],k)
j.push(new A.X("label",e,e,B.lv,e,e,a2,e))}j.push(f)
if(a1)j.push(A.H(A.a([new A.k(a,e)],k),e,"arcane-text-input-error",e,B.kR))
else{b=b.as
if(b!=null)j.push(A.H(A.a([new A.k(b,e)],k),e,"arcane-text-input-helper",e,B.lh))}return new A.c(e,"arcane-text-input-wrapper",l,a0,e,j,e)},
kg(){var s=A.t(t.N,t.v),r=this.c
if(r.ch!=null)s.i(0,"input",new A.t5(this))
if(r.cy!=null)s.i(0,"keydown",new A.t6(this))
return s}}
A.t5.prototype={
$1(a){var s=A.a7(A.p(a).target)
if(s!=null)s.gfh()},
$S:4}
A.t6.prototype={
$1(a){A.p(a).gnw()},
$S:4}
A.ly.prototype={
l(a){var s,r,q,p,o=null,n=this.c,m=n.a,l=n.b,k=l?"on":"",j=n.e,i=A.CB(o,j,m,"single",k),h=t.gm,g=A.a([A.CC(j,m,l,"on")],h)
if(!j)g.push(A.zm(new A.ih(m,"toggle","on")))
s=this.lX(n,A.nf(g))
n=n.w
if(n==null)return s
g=j?"var(--muted-foreground)":"var(--foreground)"
r=t.N
g=A.B(A.j(["font-size","var(--font-size-sm)","font-weight","var(--font-weight-medium)","color",g,"user-select","none","line-height","1"],r,r))
q=t.i
p=A.a([s,A.H(A.a([new A.k(n,o)],q),o,"arcane-toggle-label",o,g)],q)
t.f.a(i)
t.kT.a(p)
n=l?"checked":"unchecked"
h=A.nf(A.a([i,A.j(["data-state",n,"data-disabled",""+j],r,r)],h))
return new A.nb("arcane-toggle-wrapper",A.B(A.j(["display","inline-flex","align-items","center","gap","var(--space-2)","cursor",j?"not-allowed":"pointer"],r,r)),h,p,o)}}
A.oi.prototype={
E(){return"ComponentSize."+this.b}}
A.oh.prototype={
E(){return"ColorVariant."+this.b}}
A.o1.prototype={
E(){return"Brightness."+this.b}}
A.fO.prototype={
aO(a){t.cC.a(a)
return this.f!==a.f||this.r!==a.r}}
A.li.prototype={}
A.lg.prototype={}
A.k3.prototype={}
A.jc.prototype={}
A.lx.prototype={}
A.jV.prototype={}
A.t7.prototype={}
A.qK.prototype={}
A.qn.prototype={
E(){return"MainAxisAlignment."+this.b},
gaT(){switch(this.a){case 0:return"flex-start"
case 1:return"flex-end"
case 2:return"center"
case 3:return"space-between"
case 4:return"space-around"
case 5:return"space-evenly"}}}
A.oH.prototype={
E(){return"CrossAxisAlignment."+this.b},
gaT(){switch(this.a){case 0:return"flex-start"
case 1:return"flex-end"
case 2:return"center"
case 3:return"stretch"
case 4:return"baseline"}}}
A.qo.prototype={
E(){return"MainAxisSize."+this.b}}
A.pl.prototype={
E(){return"FontWeight."+this.b},
gfh(){switch(this.a){case 0:return 100
case 1:return 200
case 2:return 300
case 3:return 400
case 4:return 500
case 5:return 600
case 6:return 700
case 7:return 800
case 8:return 900}}}
A.j7.prototype={
l(a){var s="(function() {\n  'use strict';\nconst ARCANE = (window.Arcane = window.Arcane || {});\nARCANE.version = '1';\nARCANE.surfaces = ARCANE.surfaces || {};\nARCANE.groups = ARCANE.groups || {};\nARCANE.tabs = ARCANE.tabs || {};\nARCANE.panels = ARCANE.panels || {};\nARCANE.stack = ARCANE.stack || [];\nARCANE.actions = ARCANE.actions || {};\nARCANE.scripts = ARCANE.scripts || {};\nARCANE.config = ARCANE.config || {\n  reducedMotion: window.matchMedia &&\n    window.matchMedia('(prefers-reduced-motion: reduce)').matches,\n  storageKey: 'arcane.state',\n  themeStorageKey: 'arcane.theme'\n};\n\nconst FOCUSABLE_SELECTOR = [\n  'a[href]',\n  'area[href]',\n  'button:not([disabled])',\n  'input:not([disabled]):not([type=\"hidden\"])',\n  'select:not([disabled])',\n  'textarea:not([disabled])',\n  'iframe',\n  'object',\n  'embed',\n  '[tabindex]:not([tabindex=\"-1\"])',\n  '[contenteditable=\"true\"]'\n].join(',');\n\nfunction dec(s) {\n  if (s == null) return '';\n  try { return decodeURIComponent(s); } catch (e) { return s; }\n}\n\nfunction enc(s) {\n  if (s == null) return '';\n  try { return encodeURIComponent(s); } catch (e) { return s; }\n}\n\nfunction parseAction(str) {\n  if (!str) return null;\n  const trimmed = str.trim();\n  if (!trimmed) return null;\n  const parts = trimmed.split(/\\s+/);\n  const verb = parts[0];\n  const args = parts.slice(1).map(dec);\n  return { verb: verb, args: args };\n}\n\nfunction parseActions(str) {\n  if (!str) return [];\n  return str.split(';').map(parseAction).filter(Boolean);\n}\n\nfunction fireEvent(el, name, detail) {\n  if (!el) el = document;\n  const evt = new CustomEvent(name, {\n    bubbles: true,\n    cancelable: true,\n    detail: detail || {}\n  });\n  el.dispatchEvent(evt);\n  return evt;\n}\n\nfunction getFocusable(root) {\n  if (!root) return [];\n  const list = root.querySelectorAll(FOCUSABLE_SELECTOR);\n  const out = [];\n  for (let i = 0; i < list.length; i++) {\n    const el = list[i];\n    if (el.offsetParent === null && el.getClientRects().length === 0) continue;\n    if (el.hasAttribute('disabled')) continue;\n    if (el.getAttribute('aria-hidden') === 'true') continue;\n    out.push(el);\n  }\n  return out;\n}\n\nfunction findClosest(el, sel) {\n  if (!el) return null;\n  if (el.closest) return el.closest(sel);\n  let cur = el;\n  while (cur && cur.nodeType === 1) {\n    if (cur.matches && cur.matches(sel)) return cur;\n    cur = cur.parentNode;\n  }\n  return null;\n}\n\nfunction querySurface(type, id) {\n  if (!id) return null;\n  const sel = '[data-arcane-surface=\"' + type + '\"][data-arcane-id=\"' +\n    cssEscape(id) + '\"]';\n  return document.querySelector(sel);\n}\n\nfunction querySurfaceById(id) {\n  if (!id) return null;\n  const sel = '[data-arcane-surface][data-arcane-id=\"' + cssEscape(id) + '\"]';\n  return document.querySelector(sel);\n}\n\nfunction cssEscape(value) {\n  if (window.CSS && window.CSS.escape) return window.CSS.escape(value);\n  return String(value).replace(/[!\"#$%&'()*+,./:;<=>?@\\[\\\\\\]^`{|}~]/g, '\\\\$&');\n}\n\nfunction withinSurface(el, type) {\n  return findClosest(el, '[data-arcane-surface' +\n    (type ? '=\"' + type + '\"' : '') + ']');\n}\n\nfunction setBoolAttr(el, name, value) {\n  if (!el) return;\n  if (value) el.setAttribute(name, '');\n  else el.removeAttribute(name);\n}\n\nfunction readJson(str, fallback) {\n  if (!str) return fallback;\n  try { return JSON.parse(str); } catch (e) { return fallback; }\n}\n\nfunction debounce(fn, delay) {\n  let t = null;\n  return function() {\n    const args = arguments;\n    const ctx = this;\n    if (t) clearTimeout(t);\n    t = setTimeout(function() { fn.apply(ctx, args); }, delay);\n  };\n}\n\nfunction nextFrame(fn) {\n  if (window.requestAnimationFrame) {\n    requestAnimationFrame(function() { requestAnimationFrame(fn); });\n  } else {\n    setTimeout(fn, 16);\n  }\n}\n\nARCANE.util = {\n  parseAction: parseAction,\n  parseActions: parseActions,\n  fireEvent: fireEvent,\n  getFocusable: getFocusable,\n  findClosest: findClosest,\n  cssEscape: cssEscape,\n  querySurface: querySurface,\n  querySurfaceById: querySurfaceById,\n  enc: enc,\n  dec: dec,\n  readJson: readJson,\n  debounce: debounce,\n  nextFrame: nextFrame\n};\n\nfunction surfaceState(el) {\n  if (!el) return 'closed';\n  return el.getAttribute('data-arcane-state') || 'closed';\n}\n\nfunction surfaceIsOpen(el) {\n  return surfaceState(el) === 'open';\n}\n\nfunction surfaceType(el) {\n  if (!el) return null;\n  return el.getAttribute('data-arcane-surface');\n}\n\nfunction surfaceId(el) {\n  if (!el) return null;\n  return el.getAttribute('data-arcane-id');\n}\n\nfunction surfaceGroup(el) {\n  if (!el) return null;\n  return el.getAttribute('data-arcane-surface-group');\n}\n\nfunction trapFocus(surface, e) {\n  if (!surface) return;\n  if (surface.getAttribute('data-arcane-focus-trap') === 'false') return;\n  const focusable = getFocusable(surface);\n  if (focusable.length === 0) {\n    e.preventDefault();\n    surface.focus();\n    return;\n  }\n  const first = focusable[0];\n  const last = focusable[focusable.length - 1];\n  if (e.shiftKey) {\n    if (document.activeElement === first || !surface.contains(document.activeElement)) {\n      e.preventDefault();\n      last.focus();\n    }\n  } else {\n    if (document.activeElement === last) {\n      e.preventDefault();\n      first.focus();\n    }\n  }\n}\n\nfunction applyAnchor(surfaceEl) {\n  if (!surfaceEl) return;\n  const anchorId = surfaceEl.getAttribute('data-arcane-anchor');\n  if (!anchorId) return;\n  const anchorEl = document.querySelector(\n    '[data-arcane-anchor-id=\"' + cssEscape(anchorId) + '\"]'\n  );\n  if (!anchorEl) return;\n  positionAnchored(surfaceEl, anchorEl);\n}\n\nfunction positionAnchored(surfaceEl, anchorEl) {\n  const placement = surfaceEl.getAttribute('data-arcane-anchor-placement') || 'bottom';\n  const align = surfaceEl.getAttribute('data-arcane-anchor-align') || 'start';\n  const offset = parseInt(\n    surfaceEl.getAttribute('data-arcane-anchor-offset') || '8', 10\n  );\n  const rect = anchorEl.getBoundingClientRect();\n  const sw = surfaceEl.offsetWidth;\n  const sh = surfaceEl.offsetHeight;\n  const vw = window.innerWidth;\n  const vh = window.innerHeight;\n  let top = 0;\n  let left = 0;\n\n  let actualPlacement = placement;\n  if (placement === 'bottom' && rect.bottom + offset + sh > vh && rect.top - offset - sh > 0) {\n    actualPlacement = 'top';\n  } else if (placement === 'top' && rect.top - offset - sh < 0 && rect.bottom + offset + sh < vh) {\n    actualPlacement = 'bottom';\n  } else if (placement === 'right' && rect.right + offset + sw > vw && rect.left - offset - sw > 0) {\n    actualPlacement = 'left';\n  } else if (placement === 'left' && rect.left - offset - sw < 0 && rect.right + offset + sw < vw) {\n    actualPlacement = 'right';\n  }\n\n  if (actualPlacement === 'bottom') {\n    top = rect.bottom + offset;\n    left = align === 'end' ? rect.right - sw : (align === 'center' ? rect.left + (rect.width - sw) / 2 : rect.left);\n  } else if (actualPlacement === 'top') {\n    top = rect.top - sh - offset;\n    left = align === 'end' ? rect.right - sw : (align === 'center' ? rect.left + (rect.width - sw) / 2 : rect.left);\n  } else if (actualPlacement === 'right') {\n    left = rect.right + offset;\n    top = align === 'end' ? rect.bottom - sh : (align === 'center' ? rect.top + (rect.height - sh) / 2 : rect.top);\n  } else if (actualPlacement === 'left') {\n    left = rect.left - sw - offset;\n    top = align === 'end' ? rect.bottom - sh : (align === 'center' ? rect.top + (rect.height - sh) / 2 : rect.top);\n  }\n\n  if (left < 4) left = 4;\n  if (top < 4) top = 4;\n  if (left + sw > vw - 4) left = vw - sw - 4;\n  if (top + sh > vh - 4) top = vh - sh - 4;\n\n  surfaceEl.style.position = 'fixed';\n  surfaceEl.style.top = top + 'px';\n  surfaceEl.style.left = left + 'px';\n  surfaceEl.setAttribute('data-arcane-actual-placement', actualPlacement);\n}\n\nfunction openSurface(type, id, opts) {\n  opts = opts || {};\n  const el = querySurface(type, id);\n  if (!el) return false;\n  if (surfaceIsOpen(el)) return true;\n\n  const groupName = surfaceGroup(el);\n  const exclusive = el.getAttribute('data-arcane-exclusive') === 'true';\n  if (groupName) {\n    const peers = document.querySelectorAll(\n      '[data-arcane-surface-group=\"' + cssEscape(groupName) + '\"][data-arcane-state=\"open\"]'\n    );\n    for (let i = 0; i < peers.length; i++) {\n      if (peers[i] !== el) {\n        closeSurface(surfaceType(peers[i]), surfaceId(peers[i]), { silent: true });\n      }\n    }\n  }\n\n  if (exclusive) {\n    const all = document.querySelectorAll(\n      '[data-arcane-surface=\"' + type + '\"][data-arcane-state=\"open\"]'\n    );\n    for (let i = 0; i < all.length; i++) {\n      if (all[i] !== el) closeSurface(type, surfaceId(all[i]), { silent: true });\n    }\n  }\n\n  if (opts.trigger) {\n    el._arcanePrevFocus = opts.trigger;\n  } else {\n    el._arcanePrevFocus = document.activeElement;\n  }\n\n  el.removeAttribute('hidden');\n  el.setAttribute('data-arcane-state', 'open');\n  el.setAttribute('aria-hidden', 'false');\n\n  applyAnchor(el);\n\n  ARCANE.stack.push({ type: type, id: id, el: el });\n\n  if (type === 'dialog' || type === 'sheet' || type === 'drawer') {\n    document.body.classList.add('arcane-overlay-open');\n    document.body.setAttribute('data-arcane-overlay-open', 'true');\n  }\n\n  nextFrame(function() {\n    el.classList.add('arcane-surface-open');\n    el.classList.remove('arcane-surface-closing');\n\n    const focusTarget = el.querySelector('[data-arcane-autofocus]') ||\n      getFocusable(el)[0];\n    if (focusTarget) {\n      try { focusTarget.focus({ preventScroll: false }); } catch (e) { focusTarget.focus(); }\n    } else if (el.tabIndex >= 0) {\n      el.focus();\n    }\n  });\n\n  fireEvent(el, 'arcane:open', { type: type, id: id });\n  fireEvent(document, 'arcane:surface-open', { type: type, id: id });\n\n  if (!opts.skipAnchorListener && el.getAttribute('data-arcane-anchor')) {\n    el._arcaneAnchorReposition = function() { applyAnchor(el); };\n    window.addEventListener('resize', el._arcaneAnchorReposition);\n    window.addEventListener('scroll', el._arcaneAnchorReposition, true);\n  }\n\n  return true;\n}\n\nfunction closeSurface(type, id, opts) {\n  opts = opts || {};\n  let el;\n  if (id) {\n    el = querySurface(type, id);\n  } else {\n    const list = document.querySelectorAll(\n      '[data-arcane-surface=\"' + type + '\"][data-arcane-state=\"open\"]'\n    );\n    if (list.length > 0) el = list[list.length - 1];\n  }\n  if (!el) return false;\n  if (!surfaceIsOpen(el)) return false;\n\n  el.setAttribute('data-arcane-state', 'closed');\n  el.setAttribute('aria-hidden', 'true');\n  el.classList.remove('arcane-surface-open');\n  el.classList.add('arcane-surface-closing');\n\n  ARCANE.stack = ARCANE.stack.filter(function(entry) {\n    return entry.el !== el;\n  });\n\n  const stillOpen = document.querySelectorAll(\n    '[data-arcane-surface=\"dialog\"][data-arcane-state=\"open\"], ' +\n    '[data-arcane-surface=\"sheet\"][data-arcane-state=\"open\"], ' +\n    '[data-arcane-surface=\"drawer\"][data-arcane-state=\"open\"]'\n  ).length;\n  if (stillOpen === 0) {\n    document.body.classList.remove('arcane-overlay-open');\n    document.body.removeAttribute('data-arcane-overlay-open');\n  }\n\n  if (el._arcaneAnchorReposition) {\n    window.removeEventListener('resize', el._arcaneAnchorReposition);\n    window.removeEventListener('scroll', el._arcaneAnchorReposition, true);\n    el._arcaneAnchorReposition = null;\n  }\n\n  const restoreFocus = el.getAttribute('data-arcane-restore-focus') !== 'false';\n  const prevFocus = el._arcanePrevFocus;\n\n  const finalize = function() {\n    el.setAttribute('hidden', '');\n    el.classList.remove('arcane-surface-closing');\n    el.style.position = '';\n    el.style.top = '';\n    el.style.left = '';\n    el.removeAttribute('data-arcane-actual-placement');\n    if (restoreFocus && prevFocus && prevFocus.focus) {\n      try { prevFocus.focus({ preventScroll: true }); } catch (e) { prevFocus.focus(); }\n    }\n    el._arcanePrevFocus = null;\n  };\n\n  if (ARCANE.config.reducedMotion || opts.immediate) {\n    finalize();\n  } else {\n    const cs = window.getComputedStyle(el);\n    const dur = parseFloat(cs.transitionDuration || '0') || 0;\n    if (dur > 0) {\n      let done = false;\n      const handler = function() {\n        if (done) return;\n        done = true;\n        el.removeEventListener('transitionend', handler);\n        finalize();\n      };\n      el.addEventListener('transitionend', handler);\n      setTimeout(handler, dur * 1000 + 100);\n    } else {\n      finalize();\n    }\n  }\n\n  if (!opts.silent) {\n    fireEvent(el, 'arcane:close', { type: type, id: id });\n    fireEvent(document, 'arcane:surface-close', { type: type, id: id });\n  }\n  return true;\n}\n\nfunction toggleSurface(type, id, opts) {\n  const el = querySurface(type, id);\n  if (!el) return false;\n  if (surfaceIsOpen(el)) return closeSurface(type, id, opts);\n  return openSurface(type, id, opts);\n}\n\nfunction dismissTopSurface() {\n  if (ARCANE.stack.length === 0) return false;\n  const top = ARCANE.stack[ARCANE.stack.length - 1];\n  return closeSurface(top.type, top.id);\n}\n\nARCANE.surfaces.open = openSurface;\nARCANE.surfaces.close = closeSurface;\nARCANE.surfaces.toggle = toggleSurface;\nARCANE.surfaces.dismissTop = dismissTopSurface;\nARCANE.surfaces.applyAnchor = applyAnchor;\n\nfunction groupRoot(groupId) {\n  return document.querySelector('[data-arcane-group=\"' + cssEscape(groupId) +\n    '\"][data-arcane-group-mode]');\n}\n\nfunction groupValues(groupId) {\n  const root = groupRoot(groupId);\n  if (!root) return [];\n  const raw = root.getAttribute('data-arcane-group-value') || '';\n  if (!raw) return [];\n  return raw.split('\\u001f').filter(Boolean);\n}\n\nfunction groupMode(groupId) {\n  const root = groupRoot(groupId);\n  if (!root) return 'single';\n  return root.getAttribute('data-arcane-group-mode') || 'single';\n}\n\nfunction setGroupRawValues(groupId, values) {\n  const root = groupRoot(groupId);\n  if (!root) return;\n  root.setAttribute('data-arcane-group-value', values.join('\\u001f'));\n  syncGroupItems(groupId, values);\n  fireEvent(root, 'arcane:change', { groupId: groupId, value: values });\n  const changeAction = root.getAttribute('data-arcane-group-change');\n  if (changeAction) {\n    runActions(changeAction, { trigger: root, groupId: groupId, value: values });\n  }\n}\n\nfunction syncGroupItems(groupId, values) {\n  const root = groupRoot(groupId);\n  if (!root) return;\n  const items = document.querySelectorAll(\n    '[data-arcane-group=\"' + cssEscape(groupId) + '\"][data-arcane-value]'\n  );\n  for (let i = 0; i < items.length; i++) {\n    const item = items[i];\n    if (item === root) continue;\n    const v = item.getAttribute('data-arcane-value');\n    const selected = values.indexOf(v) >= 0;\n    item.setAttribute('data-arcane-state', selected ? 'selected' : 'unselected');\n    if (item.getAttribute('role') === 'option' || item.getAttribute('role') === 'menuitemcheckbox' ||\n        item.getAttribute('role') === 'menuitemradio') {\n      item.setAttribute('aria-selected', selected ? 'true' : 'false');\n      item.setAttribute('aria-checked', selected ? 'true' : 'false');\n    }\n    if (item.tagName === 'INPUT' && (item.type === 'checkbox' || item.type === 'radio')) {\n      item.checked = selected;\n    }\n  }\n}\n\nfunction setGroupValue(groupId, value) {\n  const mode = groupMode(groupId);\n  if (mode === 'multi') {\n    setGroupRawValues(groupId, value ? [value] : []);\n  } else {\n    setGroupRawValues(groupId, value ? [value] : []);\n  }\n}\n\nfunction toggleGroupValue(groupId, value) {\n  const mode = groupMode(groupId);\n  const cur = groupValues(groupId);\n  if (mode === 'multi') {\n    const idx = cur.indexOf(value);\n    let next;\n    if (idx >= 0) {\n      next = cur.slice();\n      next.splice(idx, 1);\n    } else {\n      const root = groupRoot(groupId);\n      const max = root ? parseInt(root.getAttribute('data-arcane-group-max') || '0', 10) : 0;\n      if (max > 0 && cur.length >= max) {\n        next = cur.slice(1);\n        next.push(value);\n      } else {\n        next = cur.concat([value]);\n      }\n    }\n    setGroupRawValues(groupId, next);\n  } else {\n    if (cur.length === 1 && cur[0] === value) {\n      const root = groupRoot(groupId);\n      if (root && root.getAttribute('data-arcane-group-required') === 'true') return;\n      setGroupRawValues(groupId, []);\n    } else {\n      setGroupRawValues(groupId, [value]);\n    }\n  }\n}\n\nfunction clearGroup(groupId) {\n  setGroupRawValues(groupId, []);\n}\n\nARCANE.groups.values = groupValues;\nARCANE.groups.mode = groupMode;\nARCANE.groups.set = setGroupValue;\nARCANE.groups.toggle = toggleGroupValue;\nARCANE.groups.clear = clearGroup;\n\nfunction activateTab(groupId, tabId) {\n  const triggers = document.querySelectorAll(\n    '[data-arcane-tabs-group=\"' + cssEscape(groupId) +\n    '\"][data-arcane-tab]'\n  );\n  for (let i = 0; i < triggers.length; i++) {\n    const t = triggers[i];\n    const id = t.getAttribute('data-arcane-tab');\n    const active = id === tabId;\n    t.setAttribute('data-arcane-state', active ? 'active' : 'inactive');\n    if (t.getAttribute('role') === 'tab') {\n      t.setAttribute('aria-selected', active ? 'true' : 'false');\n      t.setAttribute('tabindex', active ? '0' : '-1');\n    }\n  }\n  const panels = document.querySelectorAll(\n    '[data-arcane-tabs-group=\"' + cssEscape(groupId) +\n    '\"][data-arcane-tab-panel]'\n  );\n  for (let i = 0; i < panels.length; i++) {\n    const p = panels[i];\n    const id = p.getAttribute('data-arcane-tab-panel');\n    const active = id === tabId;\n    p.setAttribute('data-arcane-state', active ? 'active' : 'inactive');\n    if (active) p.removeAttribute('hidden');\n    else p.setAttribute('hidden', '');\n  }\n  const containers = document.querySelectorAll(\n    '[data-arcane-tabs-group=\"' + cssEscape(groupId) +\n    '\"]:not([data-arcane-tab]):not([data-arcane-tab-panel])'\n  );\n  for (let i = 0; i < containers.length; i++) {\n    containers[i].setAttribute('data-arcane-tabs-active', tabId);\n    fireEvent(containers[i], 'arcane:tabs-change', {\n      groupId: groupId, tabId: tabId\n    });\n  }\n}\n\nARCANE.tabs.activate = activateTab;\n\nfunction setPanelState(groupId, panelId, expanded) {\n  const trigger = document.querySelector(\n    '[data-arcane-panel-group=\"' + cssEscape(groupId) +\n    '\"][data-arcane-panel=\"' + cssEscape(panelId) + '\"]'\n  );\n  const content = document.querySelector(\n    '[data-arcane-panel-group=\"' + cssEscape(groupId) +\n    '\"][data-arcane-panel-content=\"' + cssEscape(panelId) + '\"]'\n  );\n  if (trigger) {\n    trigger.setAttribute('data-arcane-state', expanded ? 'expanded' : 'collapsed');\n    trigger.setAttribute('aria-expanded', expanded ? 'true' : 'false');\n  }\n  if (content) {\n    content.setAttribute('data-arcane-state', expanded ? 'expanded' : 'collapsed');\n    if (expanded) content.removeAttribute('hidden');\n    else content.setAttribute('hidden', '');\n  }\n}\n\nfunction expandPanel(groupId, panelId) {\n  const trigger = document.querySelector(\n    '[data-arcane-panel-group=\"' + cssEscape(groupId) +\n    '\"][data-arcane-panel=\"' + cssEscape(panelId) + '\"]'\n  );\n  if (trigger && trigger.getAttribute('data-arcane-panel-exclusive') === 'true') {\n    const all = document.querySelectorAll(\n      '[data-arcane-panel-group=\"' + cssEscape(groupId) +\n      '\"][data-arcane-panel][data-arcane-state=\"expanded\"]'\n    );\n    for (let i = 0; i < all.length; i++) {\n      const id = all[i].getAttribute('data-arcane-panel');\n      if (id !== panelId) setPanelState(groupId, id, false);\n    }\n  }\n  setPanelState(groupId, panelId, true);\n  fireEvent(document, 'arcane:panel-change', {\n    groupId: groupId, panelId: panelId, expanded: true\n  });\n}\n\nfunction collapsePanel(groupId, panelId) {\n  setPanelState(groupId, panelId, false);\n  fireEvent(document, 'arcane:panel-change', {\n    groupId: groupId, panelId: panelId, expanded: false\n  });\n}\n\nfunction togglePanel(groupId, panelId) {\n  const trigger = document.querySelector(\n    '[data-arcane-panel-group=\"' + cssEscape(groupId) +\n    '\"][data-arcane-panel=\"' + cssEscape(panelId) + '\"]'\n  );\n  if (!trigger) return;\n  const expanded = trigger.getAttribute('data-arcane-state') === 'expanded';\n  if (expanded) collapsePanel(groupId, panelId);\n  else expandPanel(groupId, panelId);\n}\n\nARCANE.panels.expand = expandPanel;\nARCANE.panels.collapse = collapsePanel;\nARCANE.panels.toggle = togglePanel;\n\nfunction stepperRoot(groupId) {\n  return document.querySelector('[data-arcane-stepper=\"' + cssEscape(groupId) +\n    '\"][data-arcane-step-active]');\n}\n\nfunction goToStep(groupId, target) {\n  const root = stepperRoot(groupId);\n  if (!root) return;\n  const cur = parseInt(root.getAttribute('data-arcane-step-active') || '0', 10);\n  const count = parseInt(root.getAttribute('data-arcane-step-count') || '1', 10);\n  let next = cur;\n  if (target === 'next') next = Math.min(cur + 1, count - 1);\n  else if (target === 'prev') next = Math.max(cur - 1, 0);\n  else {\n    const n = parseInt(target, 10);\n    if (!isNaN(n)) next = Math.max(0, Math.min(n, count - 1));\n  }\n  if (next === cur) return;\n  root.setAttribute('data-arcane-step-active', next.toString());\n  const steps = document.querySelectorAll(\n    '[data-arcane-stepper=\"' + cssEscape(groupId) + '\"][data-arcane-step]'\n  );\n  for (let i = 0; i < steps.length; i++) {\n    const stepEl = steps[i];\n    const idx = parseInt(stepEl.getAttribute('data-arcane-step') || '0', 10);\n    let state = 'pending';\n    if (idx < next) state = 'completed';\n    else if (idx === next) state = 'active';\n    stepEl.setAttribute('data-arcane-state', state);\n  }\n  fireEvent(root, 'arcane:step-change', { groupId: groupId, step: next });\n}\n\nARCANE.stepper = {\n  go: goToStep\n};\n\nfunction paginationRoot(groupId) {\n  return document.querySelector('[data-arcane-pagination=\"' + cssEscape(groupId) +\n    '\"][data-arcane-page-active]');\n}\n\nfunction goToPage(groupId, target) {\n  const root = paginationRoot(groupId);\n  if (!root) return;\n  const cur = parseInt(root.getAttribute('data-arcane-page-active') || '1', 10);\n  const count = parseInt(root.getAttribute('data-arcane-page-count') || '1', 10);\n  let next = cur;\n  if (target === 'next') next = Math.min(cur + 1, count);\n  else if (target === 'prev') next = Math.max(cur - 1, 1);\n  else if (target === 'first') next = 1;\n  else if (target === 'last') next = count;\n  else {\n    const n = parseInt(target, 10);\n    if (!isNaN(n)) next = Math.max(1, Math.min(n, count));\n  }\n  if (next === cur) return;\n  root.setAttribute('data-arcane-page-active', next.toString());\n  fireEvent(root, 'arcane:page-change', { groupId: groupId, page: next });\n}\n\nARCANE.pagination = {\n  go: goToPage\n};\n\nfunction carouselRoot(groupId) {\n  return document.querySelector('[data-arcane-carousel=\"' + cssEscape(groupId) +\n    '\"][data-arcane-carousel-active]');\n}\n\nfunction setCarouselSlide(groupId, idx) {\n  const root = carouselRoot(groupId);\n  if (!root) return;\n  const count = parseInt(root.getAttribute('data-arcane-carousel-count') || '1', 10);\n  const loop = root.getAttribute('data-arcane-carousel-loop') === 'true';\n  let next = idx;\n  if (loop) {\n    if (next < 0) next = count - 1;\n    if (next >= count) next = 0;\n  } else {\n    if (next < 0) next = 0;\n    if (next >= count) next = count - 1;\n  }\n  root.setAttribute('data-arcane-carousel-active', next.toString());\n  const slides = document.querySelectorAll(\n    '[data-arcane-carousel=\"' + cssEscape(groupId) +\n    '\"][data-arcane-carousel-slide]'\n  );\n  for (let i = 0; i < slides.length; i++) {\n    const s = slides[i];\n    const sIdx = parseInt(s.getAttribute('data-arcane-carousel-slide') || '0', 10);\n    s.setAttribute('data-arcane-state', sIdx === next ? 'active' : 'inactive');\n  }\n  fireEvent(root, 'arcane:carousel-change', { groupId: groupId, index: next });\n}\n\nfunction goToSlide(groupId, target) {\n  const root = carouselRoot(groupId);\n  if (!root) return;\n  const cur = parseInt(root.getAttribute('data-arcane-carousel-active') || '0', 10);\n  let next = cur;\n  if (target === 'next') next = cur + 1;\n  else if (target === 'prev') next = cur - 1;\n  else {\n    const n = parseInt(target, 10);\n    if (!isNaN(n)) next = n;\n  }\n  setCarouselSlide(groupId, next);\n}\n\nfunction startCarouselAutoplay(root) {\n  const ms = parseInt(root.getAttribute('data-arcane-carousel-autoplay') || '0', 10);\n  if (!ms || ms <= 0) return;\n  const groupId = root.getAttribute('data-arcane-carousel');\n  if (root._arcaneCarouselTimer) clearInterval(root._arcaneCarouselTimer);\n  root._arcaneCarouselTimer = setInterval(function() {\n    goToSlide(groupId, 'next');\n  }, ms);\n  root.addEventListener('mouseenter', function() {\n    if (root._arcaneCarouselTimer) clearInterval(root._arcaneCarouselTimer);\n  });\n  root.addEventListener('mouseleave', function() {\n    startCarouselAutoplay(root);\n  });\n}\n\nARCANE.carousel = {\n  go: goToSlide,\n  set: setCarouselSlide,\n  startAutoplay: startCarouselAutoplay\n};\n\nfunction sliderRoot(sliderId) {\n  return document.querySelector('[data-arcane-slider=\"' + cssEscape(sliderId) + '\"][data-arcane-slider-min]');\n}\n\nfunction sliderClampValue(root, value) {\n  const min = parseFloat(root.getAttribute('data-arcane-slider-min') || '0');\n  const max = parseFloat(root.getAttribute('data-arcane-slider-max') || '100');\n  const step = parseFloat(root.getAttribute('data-arcane-slider-step') || '0') || 0;\n  let v = Math.max(min, Math.min(max, value));\n  if (step > 0) {\n    const ticks = Math.round((v - min) / step);\n    v = min + ticks * step;\n  }\n  if (v < min) v = min;\n  if (v > max) v = max;\n  return v;\n}\n\nfunction sliderApplyValue(root, value, opts) {\n  opts = opts || {};\n  const sliderId = root.getAttribute('data-arcane-slider');\n  const min = parseFloat(root.getAttribute('data-arcane-slider-min') || '0');\n  const max = parseFloat(root.getAttribute('data-arcane-slider-max') || '100');\n  const range = root.getAttribute('data-arcane-slider-range') === 'true';\n  const role = opts.role || 'value';\n\n  const clamped = sliderClampValue(root, value);\n  const pct = max > min ? ((clamped - min) / (max - min)) * 100 : 0;\n\n  if (range) {\n    let lo = parseFloat(root.getAttribute('data-arcane-slider-lo') || min);\n    let hi = parseFloat(root.getAttribute('data-arcane-slider-hi') || max);\n    if (role === 'lo') {\n      lo = clamped;\n      if (lo > hi) lo = hi;\n    } else {\n      hi = clamped;\n      if (hi < lo) hi = lo;\n    }\n    root.setAttribute('data-arcane-slider-lo', String(lo));\n    root.setAttribute('data-arcane-slider-hi', String(hi));\n    const loPct = max > min ? ((lo - min) / (max - min)) * 100 : 0;\n    const hiPct = max > min ? ((hi - min) / (max - min)) * 100 : 0;\n    const fill = root.querySelector('[data-arcane-slider-fill]');\n    if (fill) {\n      fill.style.left = loPct + '%';\n      fill.style.right = (100 - hiPct) + '%';\n      fill.style.width = (hiPct - loPct) + '%';\n    }\n    const thumbLo = root.querySelector('[data-arcane-slider-thumb=\"lo\"]');\n    if (thumbLo) {\n      thumbLo.style.left = loPct + '%';\n      thumbLo.setAttribute('aria-valuenow', String(lo));\n    }\n    const thumbHi = root.querySelector('[data-arcane-slider-thumb=\"hi\"]');\n    if (thumbHi) {\n      thumbHi.style.left = hiPct + '%';\n      thumbHi.setAttribute('aria-valuenow', String(hi));\n    }\n    fireEvent(root, 'arcane:slider-change', {\n      sliderId: sliderId, lo: lo, hi: hi\n    });\n  } else {\n    root.setAttribute('data-arcane-slider-value', String(clamped));\n    const fill = root.querySelector('[data-arcane-slider-fill]');\n    if (fill) {\n      fill.style.width = pct + '%';\n    }\n    const thumb = root.querySelector('[data-arcane-slider-thumb]');\n    if (thumb) {\n      thumb.style.left = pct + '%';\n      thumb.setAttribute('aria-valuenow', String(clamped));\n    }\n    fireEvent(root, 'arcane:slider-change', {\n      sliderId: sliderId, value: clamped\n    });\n  }\n\n  const changeAction = root.getAttribute('data-arcane-slider-change');\n  if (changeAction) {\n    runActions(changeAction, { trigger: root, sliderId: sliderId });\n  }\n}\n\nfunction sliderValueFromPointer(root, clientX) {\n  const track = root.querySelector('[data-arcane-slider-track]') || root;\n  const rect = track.getBoundingClientRect();\n  if (rect.width <= 0) return 0;\n  const min = parseFloat(root.getAttribute('data-arcane-slider-min') || '0');\n  const max = parseFloat(root.getAttribute('data-arcane-slider-max') || '100');\n  const ratio = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));\n  return min + ratio * (max - min);\n}\n\nfunction sliderRoleFromTarget(root, target) {\n  const t = findClosest(target, '[data-arcane-slider-thumb]');\n  if (t) {\n    return t.getAttribute('data-arcane-slider-thumb') === 'hi' ? 'hi' : 'lo';\n  }\n  if (root.getAttribute('data-arcane-slider-range') === 'true') {\n    const lo = parseFloat(root.getAttribute('data-arcane-slider-lo') || '0');\n    const hi = parseFloat(root.getAttribute('data-arcane-slider-hi') || '100');\n    const target_value = sliderValueFromPointer(root, event.clientX);\n    return Math.abs(target_value - lo) < Math.abs(target_value - hi) ? 'lo' : 'hi';\n  }\n  return 'value';\n}\n\nfunction bindSliders() {\n  let activeSlider = null;\n  let activeRole = null;\n\n  function onMove(e) {\n    if (!activeSlider) return;\n    e.preventDefault();\n    const v = sliderValueFromPointer(activeSlider, e.clientX);\n    sliderApplyValue(activeSlider, v, { role: activeRole });\n  }\n\n  function onUp() {\n    activeSlider = null;\n    activeRole = null;\n    document.removeEventListener('pointermove', onMove);\n    document.removeEventListener('pointerup', onUp);\n    document.removeEventListener('pointercancel', onUp);\n  }\n\n  document.addEventListener('pointerdown', function(e) {\n    const root = findClosest(e.target, '[data-arcane-slider]');\n    if (!root) return;\n    if (root.getAttribute('data-arcane-disabled') === 'true') return;\n    const range = root.getAttribute('data-arcane-slider-range') === 'true';\n    let role = 'value';\n    if (range) {\n      const t = findClosest(e.target, '[data-arcane-slider-thumb]');\n      if (t) {\n        role = t.getAttribute('data-arcane-slider-thumb');\n      } else {\n        const v = sliderValueFromPointer(root, e.clientX);\n        const lo = parseFloat(root.getAttribute('data-arcane-slider-lo') || '0');\n        const hi = parseFloat(root.getAttribute('data-arcane-slider-hi') || '100');\n        role = Math.abs(v - lo) < Math.abs(v - hi) ? 'lo' : 'hi';\n      }\n    }\n    activeSlider = root;\n    activeRole = role;\n    const v = sliderValueFromPointer(root, e.clientX);\n    sliderApplyValue(root, v, { role: role });\n    document.addEventListener('pointermove', onMove);\n    document.addEventListener('pointerup', onUp);\n    document.addEventListener('pointercancel', onUp);\n  }, true);\n\n  document.addEventListener('keydown', function(e) {\n    const thumb = findClosest(e.target, '[data-arcane-slider-thumb]');\n    if (!thumb) return;\n    const root = findClosest(thumb, '[data-arcane-slider]');\n    if (!root) return;\n    if (root.getAttribute('data-arcane-disabled') === 'true') return;\n    const role = thumb.getAttribute('data-arcane-slider-thumb');\n    const step = parseFloat(root.getAttribute('data-arcane-slider-step') || '1') || 1;\n    const min = parseFloat(root.getAttribute('data-arcane-slider-min') || '0');\n    const max = parseFloat(root.getAttribute('data-arcane-slider-max') || '100');\n    let cur;\n    if (role === 'lo' || role === 'hi') {\n      cur = parseFloat(root.getAttribute('data-arcane-slider-' + role) || '0');\n    } else {\n      cur = parseFloat(root.getAttribute('data-arcane-slider-value') || '0');\n    }\n    let next = cur;\n    if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') next = cur - step;\n    else if (e.key === 'ArrowRight' || e.key === 'ArrowUp') next = cur + step;\n    else if (e.key === 'PageDown') next = cur - step * 10;\n    else if (e.key === 'PageUp') next = cur + step * 10;\n    else if (e.key === 'Home') next = min;\n    else if (e.key === 'End') next = max;\n    else return;\n    e.preventDefault();\n    sliderApplyValue(root, next, {\n      role: role === 'lo' || role === 'hi' ? role : 'value'\n    });\n  }, true);\n}\n\nfunction setSliderValue(sliderId, value, role) {\n  const root = sliderRoot(sliderId);\n  if (!root) return;\n  const v = parseFloat(value);\n  if (isNaN(v)) return;\n  sliderApplyValue(root, v, { role: role || 'value' });\n}\n\nARCANE.slider = {\n  set: setSliderValue,\n  bind: bindSliders\n};\n\nfunction filterCommand(cmdId, query) {\n  const surface = querySurface('command', cmdId) ||\n    querySurface('popover', cmdId) ||\n    document.querySelector('[data-arcane-command=\"' + cssEscape(cmdId) + '\"]');\n  if (!surface) return;\n  const q = (query || '').toLowerCase().trim();\n  const items = surface.querySelectorAll('[data-arcane-command-item]');\n  let visibleCount = 0;\n  for (let i = 0; i < items.length; i++) {\n    const item = items[i];\n    const label = (item.getAttribute('data-label') || '').toLowerCase();\n    const keywords = (item.getAttribute('data-keywords') || '').toLowerCase();\n    const disabled = item.getAttribute('data-arcane-disabled') === 'true';\n    let matches = q === '' || label.indexOf(q) >= 0 || keywords.indexOf(q) >= 0;\n    if (matches) {\n      item.removeAttribute('hidden');\n      item.setAttribute('data-arcane-state', disabled ? 'disabled' : 'visible');\n      if (!disabled) visibleCount++;\n    } else {\n      item.setAttribute('hidden', '');\n      item.setAttribute('data-arcane-state', 'hidden');\n    }\n  }\n  const groups = surface.querySelectorAll('[data-arcane-command-group]');\n  for (let i = 0; i < groups.length; i++) {\n    const g = groups[i];\n    const groupId = g.getAttribute('data-arcane-command-group');\n    const groupItems = surface.querySelectorAll(\n      '[data-arcane-command-item][data-arcane-command-group-id=\"' + cssEscape(groupId) + '\"]:not([hidden])'\n    );\n    if (groupItems.length === 0) {\n      g.setAttribute('hidden', '');\n    } else {\n      g.removeAttribute('hidden');\n    }\n  }\n  const empty = surface.querySelector('[data-arcane-command-empty]');\n  if (empty) {\n    if (visibleCount === 0) {\n      empty.removeAttribute('hidden');\n    } else {\n      empty.setAttribute('hidden', '');\n    }\n  }\n  fireEvent(surface, 'arcane:command-filter', { id: cmdId, query: q, visible: visibleCount });\n}\n\nfunction commandSelectFirst(cmdId) {\n  const surface = querySurface('command', cmdId) ||\n    querySurface('popover', cmdId);\n  if (!surface) return;\n  const visible = surface.querySelectorAll(\n    '[data-arcane-command-item]:not([hidden]):not([data-arcane-disabled=\"true\"])'\n  );\n  if (visible.length === 0) return;\n  visible[0].click();\n}\n\nfunction bindCommandKeyboard() {\n  document.addEventListener('keydown', function(e) {\n    if (e.key !== 'Enter' && e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return;\n    const input = e.target;\n    if (!input || !input.matches || !input.matches('[data-arcane-command-input]')) return;\n    const cmdId = input.getAttribute('data-arcane-command-input');\n    const surface = querySurface('command', cmdId) ||\n      querySurface('popover', cmdId);\n    if (!surface) return;\n    const visible = Array.prototype.slice.call(surface.querySelectorAll(\n      '[data-arcane-command-item]:not([hidden]):not([data-arcane-disabled=\"true\"])'\n    ));\n    if (visible.length === 0) return;\n    let activeIdx = visible.findIndex(function(el) {\n      return el.getAttribute('data-arcane-state') === 'active';\n    });\n    if (e.key === 'Enter') {\n      e.preventDefault();\n      const target = activeIdx >= 0 ? visible[activeIdx] : visible[0];\n      if (target) target.click();\n      return;\n    }\n    e.preventDefault();\n    if (activeIdx >= 0) {\n      visible[activeIdx].setAttribute('data-arcane-state', 'visible');\n    }\n    let next = activeIdx;\n    if (e.key === 'ArrowDown') next = activeIdx < 0 ? 0 : (activeIdx + 1) % visible.length;\n    else next = activeIdx <= 0 ? visible.length - 1 : activeIdx - 1;\n    visible[next].setAttribute('data-arcane-state', 'active');\n    visible[next].scrollIntoView({ block: 'nearest' });\n  }, true);\n}\n\nARCANE.command = {\n  filter: filterCommand,\n  selectFirst: commandSelectFirst\n};\n\nfunction cycleRoot(cycleId) {\n  return document.querySelector('[data-arcane-cycle=\"' + cssEscape(cycleId) + '\"]');\n}\n\nfunction cycleValues(root) {\n  const raw = root.getAttribute('data-arcane-cycle-values');\n  if (!raw) return [];\n  try { return JSON.parse(raw); } catch (e) { return []; }\n}\n\nfunction cycleLabels(root) {\n  const raw = root.getAttribute('data-arcane-cycle-labels');\n  if (!raw) return null;\n  try { return JSON.parse(raw); } catch (e) { return null; }\n}\n\nfunction cycleStep(cycleId, delta) {\n  const root = cycleRoot(cycleId);\n  if (!root) return;\n  const values = cycleValues(root);\n  if (!values.length) return;\n  const cur = parseInt(root.getAttribute('data-arcane-cycle-active') || '0', 10);\n  const loop = root.getAttribute('data-arcane-cycle-loop') !== 'false';\n  let next = cur + delta;\n  if (loop) {\n    if (next < 0) next = values.length - 1;\n    if (next >= values.length) next = 0;\n  } else {\n    if (next < 0) next = 0;\n    if (next >= values.length) next = values.length - 1;\n  }\n  if (next === cur) return;\n  setCycleIndex(cycleId, next);\n}\n\nfunction setCycleIndex(cycleId, idx) {\n  const root = cycleRoot(cycleId);\n  if (!root) return;\n  const values = cycleValues(root);\n  if (idx < 0 || idx >= values.length) return;\n  const labels = cycleLabels(root);\n  root.setAttribute('data-arcane-cycle-active', idx.toString());\n  root.setAttribute('data-arcane-value', values[idx]);\n  const display = root.querySelector('[data-arcane-cycle-display]') || root;\n  if (display !== root) {\n    display.textContent = labels && labels[idx] != null ? labels[idx] : values[idx];\n  } else {\n    const label = root.querySelector('[data-arcane-cycle-label]');\n    if (label) {\n      label.textContent = labels && labels[idx] != null ? labels[idx] : values[idx];\n    }\n  }\n  fireEvent(root, 'arcane:cycle-change', {\n    id: cycleId, index: idx, value: values[idx]\n  });\n}\n\nARCANE.cycle = {\n  step: cycleStep,\n  set: setCycleIndex\n};\n\nfunction _calRoot(target){\n  if (!target) return null;\n  if (target.matches && target.matches('[data-arcane-calendar]')) return target;\n  return target.querySelector ? target.querySelector('[data-arcane-calendar]') : null;\n}\nfunction _calMonthLabel(d){\n  const months = ['January','February','March','April','May','June','July','August','September','October','November','December'];\n  return months[d.getMonth()] + ' ' + d.getFullYear();\n}\nfunction _calParseDate(s){\n  if (!s) return null;\n  const parts = String(s).split('-');\n  if (parts.length !== 3) return null;\n  const y = parseInt(parts[0], 10);\n  const m = parseInt(parts[1], 10) - 1;\n  const d = parseInt(parts[2], 10);\n  if (isNaN(y) || isNaN(m) || isNaN(d)) return null;\n  return new Date(y, m, d);\n}\nfunction _calFmtDate(d){\n  if (!d) return '';\n  const y = d.getFullYear();\n  const m = String(d.getMonth() + 1).padStart(2, '0');\n  const dd = String(d.getDate()).padStart(2, '0');\n  return y + '-' + m + '-' + dd;\n}\nfunction _calIsSameDay(a, b){\n  if (!a || !b) return false;\n  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();\n}\nfunction _calInRange(d, start, end){\n  if (!d || !start || !end) return false;\n  const t = d.getTime();\n  return t >= start.getTime() && t <= end.getTime();\n}\nfunction _calDisabled(d, root){\n  const min = _calParseDate(root.getAttribute('data-arcane-min'));\n  const max = _calParseDate(root.getAttribute('data-arcane-max'));\n  if (min && d.getTime() < min.getTime()) return true;\n  if (max && d.getTime() > max.getTime()) return true;\n  return false;\n}\nfunction _calWeekNumber(date){\n  const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));\n  const dayNum = d.getUTCDay() || 7;\n  d.setUTCDate(d.getUTCDate() + 4 - dayNum);\n  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));\n  return Math.ceil((((d - yearStart) / 86400000) + 1) / 7);\n}\nfunction _calHumanize(d){\n  if (!d) return '';\n  const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];\n  return months[d.getMonth()] + ' ' + d.getDate() + ', ' + d.getFullYear();\n}\nfunction _calDisplay(root){\n  const mode = root.getAttribute('data-arcane-mode') || 'single';\n  if (mode === 'range'){\n    const rs = root.getAttribute('data-arcane-range-start');\n    const re = root.getAttribute('data-arcane-range-end');\n    if (!rs || !re) return '';\n    const sd = _calParseDate(rs); const ed = _calParseDate(re);\n    return _calHumanize(sd) + ' - ' + _calHumanize(ed);\n  }\n  const sel = root.getAttribute('data-arcane-selected');\n  if (!sel) return '';\n  return _calHumanize(_calParseDate(sel));\n}\nfunction renderCalendar(root){\n  if (!root) return;\n  const year = parseInt(root.getAttribute('data-arcane-year') || String(new Date().getFullYear()), 10);\n  const month = parseInt(root.getAttribute('data-arcane-month') || String(new Date().getMonth()), 10);\n  const firstDayOfWeek = parseInt(root.getAttribute('data-arcane-first-day') || '0', 10);\n  const showWeekNumbers = root.getAttribute('data-arcane-show-week-numbers') === 'true';\n  const showToday = root.getAttribute('data-arcane-show-today') !== 'false';\n  const mode = root.getAttribute('data-arcane-mode') || 'single';\n  const selected = _calParseDate(root.getAttribute('data-arcane-selected'));\n  const rangeStart = _calParseDate(root.getAttribute('data-arcane-range-start'));\n  const rangeEnd = _calParseDate(root.getAttribute('data-arcane-range-end'));\n  const pendingStart = _calParseDate(root.getAttribute('data-arcane-pending-start'));\n  const displayMonth = new Date(year, month, 1);\n  const today = new Date();\n  today.setHours(0, 0, 0, 0);\n  const monthStart = new Date(year, month, 1);\n  const monthEnd = new Date(year, month + 1, 0);\n  const startOffset = (monthStart.getDay() - firstDayOfWeek + 7) % 7;\n  const gridStart = new Date(year, month, 1 - startOffset);\n  const id = root.getAttribute('data-arcane-id') || '';\n  let html = '';\n  html += '<div class=\"arcane-calendar-header\" data-arcane-calendar-header>';\n  html += '<button type=\"button\" class=\"arcane-calendar-nav arcane-calendar-prev\" data-arcane-action=\"calendar.prev:' + id + '\" aria-label=\"Previous month\">&#8249;</button>';\n  html += '<div class=\"arcane-calendar-label\" data-arcane-calendar-label>' + _calMonthLabel(displayMonth) + '</div>';\n  html += '<button type=\"button\" class=\"arcane-calendar-nav arcane-calendar-next\" data-arcane-action=\"calendar.next:' + id + '\" aria-label=\"Next month\">&#8250;</button>';\n  html += '</div>';\n  if (showToday){\n    html += '<div class=\"arcane-calendar-today-row\"><button type=\"button\" class=\"arcane-calendar-today\" data-arcane-action=\"calendar.today:' + id + '\">Today</button></div>';\n  }\n  const weekdays = ['Sun','Mon','Tue','Wed','Thu','Fri','Sat'];\n  html += '<div class=\"arcane-calendar-weekdays\">';\n  if (showWeekNumbers) html += '<div class=\"arcane-calendar-weekday arcane-calendar-week-num\">#</div>';\n  for (let i = 0; i < 7; i++){\n    html += '<div class=\"arcane-calendar-weekday\">' + weekdays[(firstDayOfWeek + i) % 7] + '</div>';\n  }\n  html += '</div>';\n  html += '<div class=\"arcane-calendar-grid\">';\n  let iter = new Date(gridStart.getFullYear(), gridStart.getMonth(), gridStart.getDate());\n  for (let w = 0; w < 6; w++){\n    if (showWeekNumbers){\n      const weekNo = _calWeekNumber(iter);\n      html += '<div class=\"arcane-calendar-weeknum\">' + weekNo + '</div>';\n    }\n    for (let d = 0; d < 7; d++){\n      const inMonth = iter.getMonth() === month;\n      const disabled = _calDisabled(iter, root);\n      const isToday = _calIsSameDay(iter, today);\n      const isSelected = mode === 'single' && _calIsSameDay(iter, selected);\n      const inRange = mode === 'range' && rangeStart && rangeEnd && _calInRange(iter, rangeStart, rangeEnd);\n      const isRangeStart = mode === 'range' && _calIsSameDay(iter, rangeStart);\n      const isRangeEnd = mode === 'range' && _calIsSameDay(iter, rangeEnd);\n      const isPendingStart = mode === 'range' && _calIsSameDay(iter, pendingStart);\n      const classes = ['arcane-calendar-day'];\n      if (!inMonth) classes.push('arcane-calendar-day-other-month');\n      if (disabled) classes.push('arcane-calendar-day-disabled');\n      if (isToday) classes.push('arcane-calendar-day-today');\n      if (isSelected) classes.push('arcane-calendar-day-selected');\n      if (inRange) classes.push('arcane-calendar-day-in-range');\n      if (isRangeStart) classes.push('arcane-calendar-day-range-start');\n      if (isRangeEnd) classes.push('arcane-calendar-day-range-end');\n      if (isPendingStart) classes.push('arcane-calendar-day-pending');\n      const iso = _calFmtDate(iter);\n      const label = iter.getDate();\n      if (disabled){\n        html += '<button type=\"button\" class=\"' + classes.join(' ') + '\" disabled aria-disabled=\"true\">' + label + '</button>';\n      } else {\n        html += '<button type=\"button\" class=\"' + classes.join(' ') + '\" data-arcane-action=\"calendar.select:' + id + '\" data-arcane-value=\"' + iso + '\" aria-pressed=\"' + (isSelected || isRangeStart || isRangeEnd ? 'true' : 'false') + '\">' + label + '</button>';\n      }\n      iter = new Date(iter.getFullYear(), iter.getMonth(), iter.getDate() + 1);\n    }\n    if (iter.getMonth() !== month && iter > monthEnd) break;\n  }\n  html += '</div>';\n  root.innerHTML = html;\n}\nfunction _calEmitChange(root, value){\n  let evt;\n  try { evt = new CustomEvent('arcane:change', { detail: { value: value }, bubbles: true }); }\n  catch (e){ evt = document.createEvent('CustomEvent'); evt.initCustomEvent('arcane:change', true, false, { value: value }); }\n  root.dispatchEvent(evt);\n  const id = root.getAttribute('data-arcane-id');\n  if (!id) return;\n  const trigger = document.querySelector('[data-arcane-calendar-trigger=\"' + cssEscape(id) + '\"]');\n  if (trigger){\n    const label = trigger.querySelector('[data-arcane-calendar-display]');\n    if (label) label.textContent = _calDisplay(root) || (trigger.getAttribute('data-arcane-placeholder') || '');\n  }\n}\nfunction _calResolveRoot(target){\n  if (!target) return null;\n  if (typeof target === 'string'){\n    return _calRoot(document.querySelector('[data-arcane-id=\"' + cssEscape(target) + '\"]'));\n  }\n  return _calRoot(target);\n}\nfunction _calClosePicker(id){\n  const picker = document.querySelector('[data-arcane-surface][data-arcane-calendar-anchor=\"' + cssEscape(id) + '\"]');\n  if (picker) closeSurface(surfaceType(picker), surfaceId(picker));\n}\nfunction calendarPrev(target){\n  const root = _calResolveRoot(target);\n  if (!root) return;\n  const year = parseInt(root.getAttribute('data-arcane-year'), 10);\n  const month = parseInt(root.getAttribute('data-arcane-month'), 10);\n  const d = new Date(year, month - 1, 1);\n  root.setAttribute('data-arcane-year', String(d.getFullYear()));\n  root.setAttribute('data-arcane-month', String(d.getMonth()));\n  renderCalendar(root);\n}\nfunction calendarNext(target){\n  const root = _calResolveRoot(target);\n  if (!root) return;\n  const year = parseInt(root.getAttribute('data-arcane-year'), 10);\n  const month = parseInt(root.getAttribute('data-arcane-month'), 10);\n  const d = new Date(year, month + 1, 1);\n  root.setAttribute('data-arcane-year', String(d.getFullYear()));\n  root.setAttribute('data-arcane-month', String(d.getMonth()));\n  renderCalendar(root);\n}\nfunction calendarToday(target){\n  const root = _calResolveRoot(target);\n  if (!root) return;\n  const now = new Date();\n  root.setAttribute('data-arcane-year', String(now.getFullYear()));\n  root.setAttribute('data-arcane-month', String(now.getMonth()));\n  renderCalendar(root);\n}\nfunction calendarSelect(target, value){\n  const root = _calResolveRoot(target);\n  if (!root) return;\n  const picked = _calParseDate(value);\n  if (!picked || _calDisabled(picked, root)) return;\n  const mode = root.getAttribute('data-arcane-mode') || 'single';\n  const id = root.getAttribute('data-arcane-id');\n  if (mode === 'range'){\n    const pendingStart = root.getAttribute('data-arcane-pending-start');\n    if (!pendingStart){\n      root.setAttribute('data-arcane-pending-start', value);\n      root.removeAttribute('data-arcane-range-start');\n      root.removeAttribute('data-arcane-range-end');\n      renderCalendar(root);\n    } else {\n      let startD = _calParseDate(pendingStart);\n      let endD = picked;\n      if (startD.getTime() > endD.getTime()){\n        const tmp = startD; startD = endD; endD = tmp;\n      }\n      root.setAttribute('data-arcane-range-start', _calFmtDate(startD));\n      root.setAttribute('data-arcane-range-end', _calFmtDate(endD));\n      root.removeAttribute('data-arcane-pending-start');\n      renderCalendar(root);\n      _calEmitChange(root, _calFmtDate(startD) + '/' + _calFmtDate(endD));\n      _calClosePicker(id);\n    }\n  } else {\n    root.setAttribute('data-arcane-selected', value);\n    renderCalendar(root);\n    _calEmitChange(root, value);\n    _calClosePicker(id);\n  }\n}\nfunction bindCalendars(){\n  const nodes = document.querySelectorAll('[data-arcane-calendar]:not([data-arcane-calendar-bound])');\n  for (let i = 0; i < nodes.length; i++){\n    nodes[i].setAttribute('data-arcane-calendar-bound', 'true');\n    if (!nodes[i].innerHTML.trim()) renderCalendar(nodes[i]);\n  }\n}\n\nfunction bindTimePickers() {\n  const pickers = document.querySelectorAll('[data-arcane-time-picker]');\n  for (let i = 0; i < pickers.length; i++) {\n    const picker = pickers[i];\n    if (picker.__arcaneTimeBound) continue;\n    picker.__arcaneTimeBound = true;\n    const pickerId = picker.getAttribute('data-arcane-time-picker');\n    const use24 = picker.getAttribute('data-arcane-time-24h') === 'true';\n    const clearable = picker.getAttribute('data-arcane-time-clearable') === 'true';\n    const placeholder = picker.getAttribute('data-arcane-time-placeholder') || 'Select time...';\n    const hourGroup = pickerId + '-hour';\n    const minuteGroup = pickerId + '-minute';\n    const periodGroup = pickerId + '-period';\n    function updateDisplay() {\n      const hourSet = ARCANE.groups.values(hourGroup);\n      const minuteSet = ARCANE.groups.values(minuteGroup);\n      const periodSet = ARCANE.groups.values(periodGroup);\n      const hour = hourSet.length > 0 ? hourSet[0] : null;\n      const minute = minuteSet.length > 0 ? minuteSet[0] : null;\n      const period = periodSet.length > 0 ? periodSet[0] : null;\n      const display = picker.querySelector('[data-arcane-time-display]');\n      if (!display) return;\n      if (hour === null && minute === null) {\n        display.textContent = placeholder;\n        picker.removeAttribute('data-arcane-time-value');\n        return;\n      }\n      const h = hour !== null ? parseInt(hour, 10) : 0;\n      const m = minute !== null ? parseInt(minute, 10) : 0;\n      const pad = function(n) { return n < 10 ? '0' + n : '' + n; };\n      let text;\n      if (use24) {\n        text = pad(h) + ':' + pad(m);\n      } else {\n        const p = period === 'pm' ? 'PM' : 'AM';\n        text = pad(h) + ':' + pad(m) + ' ' + p;\n      }\n      display.textContent = text;\n      picker.setAttribute('data-arcane-time-value', text);\n      fireEvent(picker, 'arcane:change', {\n        id: pickerId, hour: h, minute: m, period: period, text: text\n      });\n    }\n    document.addEventListener('arcane:change', function(ev) {\n      const detail = ev.detail || {};\n      if (detail.groupId === hourGroup || detail.groupId === minuteGroup || detail.groupId === periodGroup) {\n        updateDisplay();\n      }\n    });\n    const clear = picker.querySelector('[data-arcane-time-clear]');\n    if (clear && clearable) {\n      clear.addEventListener('click', function(ev) {\n        ev.preventDefault();\n        ev.stopPropagation();\n        ARCANE.groups.clear(hourGroup);\n        ARCANE.groups.clear(minuteGroup);\n        ARCANE.groups.clear(periodGroup);\n        updateDisplay();\n      });\n    }\n    updateDisplay();\n  }\n}\n\nARCANE.timePicker = {\n  bind: bindTimePickers\n};\n\nfunction formRoot(formId) {\n  return document.querySelector('[data-arcane-form=\"' + cssEscape(formId) + '\"]');\n}\n\nfunction fieldEls(formId, fieldName) {\n  return document.querySelectorAll(\n    '[data-arcane-form=\"' + cssEscape(formId) +\n    '\"][data-arcane-field=\"' + cssEscape(fieldName) + '\"]'\n  );\n}\n\nfunction fieldValue(formId, fieldName) {\n  const els = fieldEls(formId, fieldName);\n  if (!els.length) return null;\n  const el = els[0];\n  if (el.tagName === 'INPUT') {\n    if (el.type === 'checkbox') return el.checked;\n    if (el.type === 'radio') {\n      for (let i = 0; i < els.length; i++) if (els[i].checked) return els[i].value;\n      return null;\n    }\n    return el.value;\n  }\n  if (el.tagName === 'SELECT' || el.tagName === 'TEXTAREA') return el.value;\n  return el.getAttribute('data-arcane-field-value') || el.textContent;\n}\n\nfunction setFieldValue(formId, fieldName, value) {\n  const els = fieldEls(formId, fieldName);\n  for (let i = 0; i < els.length; i++) {\n    const el = els[i];\n    if (el.tagName === 'INPUT' && el.type === 'checkbox') {\n      el.checked = !!value;\n    } else if (el.tagName === 'INPUT' && el.type === 'radio') {\n      el.checked = el.value === value;\n    } else if (el.tagName === 'INPUT' || el.tagName === 'SELECT' ||\n      el.tagName === 'TEXTAREA') {\n      el.value = value;\n    } else {\n      el.setAttribute('data-arcane-field-value', value);\n    }\n  }\n  const root = formRoot(formId);\n  if (root) {\n    fireEvent(root, 'arcane:field-change', {\n      formId: formId, field: fieldName, value: value\n    });\n  }\n}\n\nfunction collectForm(formId) {\n  const root = formRoot(formId);\n  if (!root) return null;\n  const out = {};\n  const fields = root.querySelectorAll('[data-arcane-field]');\n  const seen = {};\n  for (let i = 0; i < fields.length; i++) {\n    const name = fields[i].getAttribute('data-arcane-field');\n    if (seen[name]) continue;\n    seen[name] = true;\n    out[name] = fieldValue(formId, name);\n  }\n  return out;\n}\n\nfunction validateField(formId, fieldName) {\n  const els = fieldEls(formId, fieldName);\n  if (!els.length) return { ok: true };\n  const el = els[0];\n  const validate = el.getAttribute('data-arcane-field-validate') || '';\n  const required = el.getAttribute('data-arcane-field-required') === 'true';\n  const value = fieldValue(formId, fieldName);\n  const errors = [];\n\n  if (required) {\n    if (value == null || value === '' || value === false) {\n      errors.push('required');\n    }\n  }\n  if (value && validate) {\n    const rules = validate.split(/\\s+/);\n    for (let i = 0; i < rules.length; i++) {\n      const r = rules[i];\n      if (r === 'email') {\n        if (!/^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$/.test(value)) errors.push('email');\n      } else if (r === 'url') {\n        try { new URL(value); } catch (e) { errors.push('url'); }\n      } else if (r.indexOf('min:') === 0) {\n        const min = parseInt(r.substring(4), 10);\n        if (typeof value === 'string' && value.length < min) errors.push('min');\n      } else if (r.indexOf('max:') === 0) {\n        const max = parseInt(r.substring(4), 10);\n        if (typeof value === 'string' && value.length > max) errors.push('max');\n      } else if (r.indexOf('pattern:') === 0) {\n        try {\n          const re = new RegExp(r.substring(8));\n          if (!re.test(value)) errors.push('pattern');\n        } catch (e) {}\n      }\n    }\n  }\n\n  for (let i = 0; i < els.length; i++) {\n    if (errors.length) {\n      els[i].setAttribute('data-arcane-field-invalid', 'true');\n      els[i].setAttribute('aria-invalid', 'true');\n    } else {\n      els[i].removeAttribute('data-arcane-field-invalid');\n      els[i].setAttribute('aria-invalid', 'false');\n    }\n  }\n  const errorEl = document.querySelector(\n    '[data-arcane-form=\"' + cssEscape(formId) +\n    '\"][data-arcane-field-error=\"' + cssEscape(fieldName) + '\"]'\n  );\n  if (errorEl) {\n    errorEl.textContent = errors.length ? errorEl.getAttribute('data-arcane-error-' + errors[0]) || errors[0] : '';\n    if (errors.length) errorEl.removeAttribute('hidden');\n    else errorEl.setAttribute('hidden', '');\n  }\n  return { ok: errors.length === 0, errors: errors };\n}\n\nfunction validateForm(formId) {\n  const root = formRoot(formId);\n  if (!root) return { ok: true };\n  const fields = root.querySelectorAll('[data-arcane-field]');\n  let allOk = true;\n  const seen = {};\n  for (let i = 0; i < fields.length; i++) {\n    const name = fields[i].getAttribute('data-arcane-field');\n    if (seen[name]) continue;\n    seen[name] = true;\n    const result = validateField(formId, name);\n    if (!result.ok) allOk = false;\n  }\n  return { ok: allOk };\n}\n\nfunction submitForm(formId) {\n  const root = formRoot(formId);\n  if (!root) return;\n  const validation = validateForm(formId);\n  if (!validation.ok) {\n    fireEvent(root, 'arcane:form-invalid', { formId: formId });\n    return;\n  }\n  const data = collectForm(formId);\n  fireEvent(root, 'arcane:form-submit', { formId: formId, data: data });\n  if (root.getAttribute('data-arcane-form-reset-on-submit') === 'true') {\n    resetForm(formId);\n  }\n}\n\nfunction resetForm(formId) {\n  const root = formRoot(formId);\n  if (!root) return;\n  if (root.tagName === 'FORM') root.reset();\n  const fields = root.querySelectorAll('[data-arcane-field]');\n  for (let i = 0; i < fields.length; i++) {\n    const el = fields[i];\n    if (el.tagName === 'INPUT') {\n      if (el.type === 'checkbox' || el.type === 'radio') el.checked = el.defaultChecked;\n      else el.value = el.defaultValue;\n    } else if (el.tagName === 'SELECT') {\n      for (let j = 0; j < el.options.length; j++) el.options[j].selected = el.options[j].defaultSelected;\n    } else if (el.tagName === 'TEXTAREA') {\n      el.value = el.defaultValue;\n    }\n    el.removeAttribute('data-arcane-field-invalid');\n    el.setAttribute('aria-invalid', 'false');\n  }\n  fireEvent(root, 'arcane:form-reset', { formId: formId });\n}\n\nARCANE.forms = {\n  submit: submitForm,\n  reset: resetForm,\n  validate: validateForm,\n  setField: setFieldValue,\n  fieldValue: fieldValue,\n  collect: collectForm\n};\n\nfunction setThemeMode(mode) {\n  document.documentElement.setAttribute('data-arcane-theme', mode);\n  document.documentElement.classList.remove('light', 'dark');\n  if (mode === 'dark' || mode === 'light') {\n    document.documentElement.classList.add(mode);\n  }\n  try { localStorage.setItem('arcane.theme.mode', mode); } catch (e) {}\n  fireEvent(document, 'arcane:theme-change', { mode: mode });\n}\n\nfunction toggleThemeMode() {\n  const cur = document.documentElement.getAttribute('data-arcane-theme') ||\n    (document.documentElement.classList.contains('dark') ? 'dark' : 'light');\n  setThemeMode(cur === 'dark' ? 'light' : 'dark');\n}\n\nfunction setStylesheet(id) {\n  document.documentElement.setAttribute('data-arcane-stylesheet', id);\n  try { localStorage.setItem('arcane.theme.stylesheet', id); } catch (e) {}\n  fireEvent(document, 'arcane:stylesheet-change', { id: id });\n}\n\nfunction setPalette(stylesheetId, paletteId) {\n  document.documentElement.setAttribute('data-arcane-stylesheet', stylesheetId);\n  document.documentElement.setAttribute('data-arcane-palette', paletteId);\n  try {\n    localStorage.setItem('arcane.theme.stylesheet', stylesheetId);\n    localStorage.setItem('arcane.theme.palette', paletteId);\n  } catch (e) {}\n  fireEvent(document, 'arcane:palette-change', { id: paletteId, stylesheetId: stylesheetId });\n}\n\nfunction hydrateThemeFromStorage() {\n  try {\n    const m = localStorage.getItem('arcane.theme.mode');\n    if (m && (m === 'light' || m === 'dark')) {\n      document.documentElement.setAttribute('data-arcane-theme', m);\n      document.documentElement.classList.remove('light', 'dark');\n      document.documentElement.classList.add(m);\n    }\n    const s = localStorage.getItem('arcane.theme.stylesheet');\n    if (s) document.documentElement.setAttribute('data-arcane-stylesheet', s);\n    const p = localStorage.getItem('arcane.theme.palette');\n    if (p) document.documentElement.setAttribute('data-arcane-palette', p);\n  } catch (e) {}\n}\n\nARCANE.theme = {\n  setMode: setThemeMode,\n  toggleMode: toggleThemeMode,\n  setStylesheet: setStylesheet,\n  setPalette: setPalette,\n  hydrate: hydrateThemeFromStorage\n};\n\nfunction ensureToastSurface() {\n  let surf = document.querySelector('[data-arcane-toast-surface]');\n  if (!surf) {\n    surf = document.createElement('div');\n    surf.setAttribute('data-arcane-toast-surface', '');\n    surf.setAttribute('role', 'region');\n    surf.setAttribute('aria-label', 'Notifications');\n    surf.setAttribute('aria-live', 'polite');\n    document.body.appendChild(surf);\n  }\n  return surf;\n}\n\nfunction showToast(payload) {\n  const surf = ensureToastSurface();\n  const el = document.createElement('div');\n  el.className = 'arcane-toast';\n  el.setAttribute('data-arcane-toast', '');\n  el.setAttribute('data-arcane-toast-variant', payload.variant || 'info');\n  el.setAttribute('role', 'status');\n  if (payload.title) {\n    const titleEl = document.createElement('div');\n    titleEl.className = 'arcane-toast-title';\n    titleEl.textContent = payload.title;\n    el.appendChild(titleEl);\n  }\n  const msgEl = document.createElement('div');\n  msgEl.className = 'arcane-toast-message';\n  msgEl.textContent = payload.message || '';\n  el.appendChild(msgEl);\n  const closeBtn = document.createElement('button');\n  closeBtn.type = 'button';\n  closeBtn.className = 'arcane-toast-close';\n  closeBtn.setAttribute('aria-label', 'Dismiss');\n  closeBtn.textContent = '\\u00d7';\n  closeBtn.addEventListener('click', function() { dismissToast(el); });\n  el.appendChild(closeBtn);\n  surf.appendChild(el);\n  nextFrame(function() { el.classList.add('arcane-toast-shown'); });\n  const duration = payload.duration || 4000;\n  if (duration > 0) {\n    setTimeout(function() { dismissToast(el); }, duration);\n  }\n  fireEvent(document, 'arcane:toast', { variant: payload.variant, message: payload.message });\n}\n\nfunction dismissToast(el) {\n  if (!el) return;\n  el.classList.remove('arcane-toast-shown');\n  el.classList.add('arcane-toast-leaving');\n  setTimeout(function() {\n    if (el.parentNode) el.parentNode.removeChild(el);\n  }, 250);\n}\n\nARCANE.toast = {\n  show: showToast,\n  dismiss: dismissToast\n};\n\nfunction copyText(text, feedback) {\n  if (!navigator.clipboard) {\n    const ta = document.createElement('textarea');\n    ta.value = text;\n    ta.style.position = 'fixed';\n    ta.style.left = '-9999px';\n    document.body.appendChild(ta);\n    ta.select();\n    try {\n      document.execCommand('copy');\n      ta.remove();\n      if (feedback) showToast({ message: feedback, variant: 'success' });\n      fireEvent(document, 'arcane:copy', { text: text });\n      return Promise.resolve(true);\n    } catch (e) {\n      ta.remove();\n      return Promise.reject(e);\n    }\n  }\n  return navigator.clipboard.writeText(text).then(function() {\n    if (feedback) showToast({ message: feedback, variant: 'success' });\n    fireEvent(document, 'arcane:copy', { text: text });\n    return true;\n  });\n}\n\nARCANE.copy = copyText;\n\nfunction navGo(href) {\n  const evt = fireEvent(document, 'arcane:nav', { href: href });\n  if (!evt.defaultPrevented) {\n    window.location.href = href;\n  }\n}\n\nfunction navExternal(href) {\n  window.open(href, '_blank', 'noopener,noreferrer');\n}\n\nfunction navBack() {\n  if (window.history.length > 1) window.history.back();\n}\n\nARCANE.nav = {\n  go: navGo,\n  external: navExternal,\n  back: navBack\n};\n\nfunction runAction(action, ctx) {\n  if (!action) return;\n  const verb = action.verb;\n  const args = action.args;\n  ctx = ctx || {};\n\n  switch (verb) {\n    case 'noop':\n      return;\n    case 'surface.open':\n      openSurface(args[0], args[1], { trigger: ctx.trigger });\n      return;\n    case 'surface.close':\n      closeSurface(args[0], args[1]);\n      return;\n    case 'surface.toggle':\n      toggleSurface(args[0], args[1], { trigger: ctx.trigger });\n      return;\n    case 'surface.dismiss': {\n      const surface = withinSurface(ctx.trigger);\n      if (surface) {\n        closeSurface(surfaceType(surface), surfaceId(surface));\n      } else {\n        dismissTopSurface();\n      }\n      return;\n    }\n    case 'value.set':\n      setGroupValue(args[0], args[1] || '');\n      return;\n    case 'value.toggle':\n      toggleGroupValue(args[0], args[1] || '');\n      return;\n    case 'value.clear':\n      clearGroup(args[0]);\n      return;\n    case 'tab.activate':\n      activateTab(args[0], args[1]);\n      return;\n    case 'panel.expand':\n      expandPanel(args[0], args[1]);\n      return;\n    case 'panel.collapse':\n      collapsePanel(args[0], args[1]);\n      return;\n    case 'panel.toggle':\n      togglePanel(args[0], args[1]);\n      return;\n    case 'step.go':\n      goToStep(args[0], args[1]);\n      return;\n    case 'page.go':\n      goToPage(args[0], args[1]);\n      return;\n    case 'carousel.go':\n      goToSlide(args[0], args[1]);\n      return;\n    case 'slider.set':\n      setSliderValue(args[0], args[1] || '0', args[2]);\n      return;\n    case 'cycle.next':\n      cycleStep(args[0], 1);\n      return;\n    case 'cycle.prev':\n      cycleStep(args[0], -1);\n      return;\n    case 'calendar.prev':\n      calendarPrev(args[0]);\n      return;\n    case 'calendar.next':\n      calendarNext(args[0]);\n      return;\n    case 'calendar.today':\n      calendarToday(args[0]);\n      return;\n    case 'calendar.select':\n      calendarSelect(args[0], (ctx.trigger && ctx.trigger.getAttribute) ? ctx.trigger.getAttribute('data-arcane-value') : args[1]);\n      return;\n    case 'command.filter': {\n      const inp = ctx.trigger;\n      const q = inp && inp.value !== undefined ? inp.value : (args[1] || '');\n      filterCommand(args[0], q);\n      return;\n    }\n    case 'command.selectFirst':\n      commandSelectFirst(args[0]);\n      return;\n    case 'form.submit':\n      submitForm(args[0]);\n      return;\n    case 'form.reset':\n      resetForm(args[0]);\n      return;\n    case 'form.validate':\n      validateForm(args[0]);\n      return;\n    case 'field.set':\n      setFieldValue(args[0], args[1], args[2] || '');\n      return;\n    case 'copy':\n      copyText(args[0] || '', args[1]);\n      return;\n    case 'nav.go':\n      navGo(args[0]);\n      return;\n    case 'nav.external':\n      navExternal(args[0]);\n      return;\n    case 'nav.back':\n      navBack();\n      return;\n    case 'theme.mode.set':\n      setThemeMode(args[0]);\n      return;\n    case 'theme.mode.toggle':\n      toggleThemeMode();\n      return;\n    case 'theme.stylesheet.set':\n      setStylesheet(args[0]);\n      return;\n    case 'theme.palette.set': {\n      const parts = (args[0] || '').split('/');\n      setPalette(parts[0] || '', parts[1] || '');\n      return;\n    }\n    case 'toast.show': {\n      const payload = readJson(args[0], { message: args[0] || '' });\n      showToast(payload);\n      return;\n    }\n    case 'event.dispatch': {\n      const detail = args[1] ? readJson(args[1], {}) : {};\n      fireEvent(document, args[0], detail);\n      return;\n    }\n    case 'script.run': {\n      const fn = ARCANE.scripts[args[0]];\n      if (typeof fn === 'function') {\n        fn.apply(null, args.slice(1));\n      }\n      return;\n    }\n    default:\n      if (verb && verb.indexOf(':') > 0) {\n        fireEvent(document, verb, { args: args, ctx: ctx });\n        return;\n      }\n      console.warn('[arcane] unknown action:', verb);\n  }\n}\n\nfunction runActions(str, ctx) {\n  if (!str) return;\n  const list = parseActions(str);\n  for (let i = 0; i < list.length; i++) {\n    runAction(list[i], ctx);\n  }\n}\n\nARCANE.run = runActions;\nARCANE.runOne = runAction;\n\nfunction findActionTarget(el, attr) {\n  let cur = el;\n  while (cur && cur !== document) {\n    if (cur.nodeType === 1 && cur.hasAttribute(attr)) return cur;\n    cur = cur.parentNode;\n  }\n  return null;\n}\n\nfunction shouldHandleClick(el) {\n  if (!el) return false;\n  if (el.hasAttribute('data-arcane-disabled')) return false;\n  if (el.hasAttribute('disabled')) return false;\n  return true;\n}\n\nfunction autoDismissAfterAction(trigger) {\n  if (!trigger) return;\n  if (trigger.getAttribute('data-arcane-keep-open') === 'true') return;\n  const surf = withinSurface(trigger);\n  if (!surf) return;\n  const stype = surfaceType(surf);\n  if (stype !== 'menu' && stype !== 'context-menu' && stype !== 'popover') return;\n  if (surf.getAttribute('data-arcane-keep-open-on-action') === 'true') return;\n  const sid = surfaceId(surf);\n  setTimeout(function() { closeSurface(stype, sid); }, 0);\n}\n\nfunction onDocumentClick(e) {\n  if (e.defaultPrevented) return;\n\n  const trigger = findActionTarget(e.target, 'data-arcane-action');\n  if (trigger && shouldHandleClick(trigger)) {\n    if (trigger.tagName === 'A' && trigger.getAttribute('href')) {\n      const href = trigger.getAttribute('href');\n      if (href.indexOf('#') !== 0 && trigger.target !== '_blank') {\n        e.preventDefault();\n      }\n    }\n    if (trigger.tagName === 'BUTTON' || trigger.getAttribute('role') === 'button') {\n      e.preventDefault();\n    }\n    runActions(trigger.getAttribute('data-arcane-action'), { trigger: trigger, event: e });\n    autoDismissAfterAction(trigger);\n    return;\n  }\n\n  if (ARCANE.stack.length > 0) {\n    const top = ARCANE.stack[ARCANE.stack.length - 1];\n    const surfaceEl = top.el;\n    if (surfaceEl && !surfaceEl.contains(e.target)) {\n      const scrimCloses = surfaceEl.getAttribute('data-arcane-scrim-closes') !== 'false';\n      const dismissible = surfaceEl.getAttribute('data-arcane-dismissible') !== 'false';\n      if (scrimCloses && dismissible) {\n        if ((top.type === 'popover' || top.type === 'menu' || top.type === 'context-menu' || top.type === 'tooltip') ||\n            e.target.classList.contains('arcane-overlay-scrim') ||\n            e.target.hasAttribute('data-arcane-scrim') ||\n            (top.type === 'dialog' || top.type === 'sheet' || top.type === 'drawer')) {\n          if (top.type === 'dialog' || top.type === 'sheet' || top.type === 'drawer') {\n            if (e.target.classList.contains('arcane-overlay-scrim') ||\n                e.target.hasAttribute('data-arcane-scrim') ||\n                e.target === surfaceEl.parentElement) {\n              closeSurface(top.type, top.id);\n            }\n          } else {\n            closeSurface(top.type, top.id);\n          }\n        }\n      }\n    }\n  }\n}\n\nfunction onDocumentChange(e) {\n  const target = findActionTarget(e.target, 'data-arcane-change');\n  if (target) {\n    runActions(target.getAttribute('data-arcane-change'), { trigger: target, event: e });\n  }\n  if (e.target.matches && e.target.matches('[data-arcane-form] [data-arcane-field]')) {\n    const formEl = findClosest(e.target, '[data-arcane-form]');\n    if (formEl) {\n      const fid = formEl.getAttribute('data-arcane-form');\n      const fname = e.target.getAttribute('data-arcane-field');\n      if (fid && fname) validateField(fid, fname);\n    }\n  }\n}\n\nfunction onDocumentInput(e) {\n  const target = findActionTarget(e.target, 'data-arcane-input');\n  if (target) {\n    runActions(target.getAttribute('data-arcane-input'), { trigger: target, event: e });\n  }\n}\n\nfunction onDocumentSubmit(e) {\n  const formEl = findActionTarget(e.target, 'data-arcane-form');\n  if (formEl) {\n    e.preventDefault();\n    const fid = formEl.getAttribute('data-arcane-form');\n    if (fid) submitForm(fid);\n  }\n  const target = findActionTarget(e.target, 'data-arcane-submit');\n  if (target) {\n    runActions(target.getAttribute('data-arcane-submit'), { trigger: target, event: e });\n  }\n}\n\nfunction onDocumentDblClick(e) {\n  const target = findActionTarget(e.target, 'data-arcane-dblclick');\n  if (target) {\n    runActions(target.getAttribute('data-arcane-dblclick'), { trigger: target, event: e });\n  }\n}\n\nfunction onDocumentContextMenu(e) {\n  const target = findActionTarget(e.target, 'data-arcane-contextmenu');\n  if (target) {\n    e.preventDefault();\n    runActions(target.getAttribute('data-arcane-contextmenu'), { trigger: target, event: e });\n  }\n}\n\nfunction onDocumentKeyDown(e) {\n  if (e.key === 'Escape') {\n    if (ARCANE.stack.length > 0) {\n      const top = ARCANE.stack[ARCANE.stack.length - 1];\n      const surfaceEl = top.el;\n      if (surfaceEl && surfaceEl.getAttribute('data-arcane-escape-closes') !== 'false') {\n        closeSurface(top.type, top.id);\n        e.preventDefault();\n        e.stopPropagation();\n        return;\n      }\n    }\n  }\n  if (e.key === 'Tab' && ARCANE.stack.length > 0) {\n    const top = ARCANE.stack[ARCANE.stack.length - 1];\n    const surfaceEl = top.el;\n    if (surfaceEl && (top.type === 'dialog' || top.type === 'sheet' ||\n        surfaceEl.getAttribute('data-arcane-focus-trap') === 'true')) {\n      trapFocus(surfaceEl, e);\n    }\n  }\n  if (e.key === 'Enter' || e.key === ' ') {\n    const target = e.target;\n    if (target && target.matches && target.matches('[data-arcane-action]') &&\n        target.tagName !== 'BUTTON' && target.tagName !== 'A' &&\n        target.tagName !== 'INPUT' && target.tagName !== 'TEXTAREA' &&\n        target.tagName !== 'SELECT') {\n      e.preventDefault();\n      runActions(target.getAttribute('data-arcane-action'), { trigger: target, event: e });\n    }\n  }\n  if (e.key === 'ArrowLeft' || e.key === 'ArrowRight' ||\n      e.key === 'ArrowUp' || e.key === 'ArrowDown') {\n    handleArrowKey(e);\n  }\n\n  const keyTarget = findActionTarget(e.target, 'data-arcane-keydown');\n  if (keyTarget) {\n    runActions(keyTarget.getAttribute('data-arcane-keydown'), { trigger: keyTarget, event: e });\n  }\n}\n\nfunction handleArrowKey(e) {\n  const tab = findClosest(e.target, '[role=\"tab\"][data-arcane-tab]');\n  if (tab) {\n    const groupId = tab.getAttribute('data-arcane-tabs-group');\n    const orientation = (findClosest(tab, '[data-arcane-tabs-orientation]') || {}).getAttribute &&\n      findClosest(tab, '[data-arcane-tabs-orientation]').getAttribute('data-arcane-tabs-orientation') || 'horizontal';\n    const isHorizontal = orientation === 'horizontal';\n    const forward = isHorizontal ? e.key === 'ArrowRight' : e.key === 'ArrowDown';\n    const backward = isHorizontal ? e.key === 'ArrowLeft' : e.key === 'ArrowUp';\n    if (forward || backward) {\n      e.preventDefault();\n      const triggers = Array.prototype.slice.call(document.querySelectorAll(\n        '[data-arcane-tabs-group=\"' + cssEscape(groupId) + '\"][data-arcane-tab]'\n      ));\n      const idx = triggers.indexOf(tab);\n      let next = forward ? idx + 1 : idx - 1;\n      if (next < 0) next = triggers.length - 1;\n      if (next >= triggers.length) next = 0;\n      const nextTab = triggers[next];\n      if (nextTab) {\n        activateTab(groupId, nextTab.getAttribute('data-arcane-tab'));\n        nextTab.focus();\n      }\n      return;\n    }\n  }\n\n  const opt = findClosest(e.target, '[data-arcane-group][data-arcane-value]');\n  if (opt) {\n    const surf = withinSurface(opt);\n    if (surf && (surfaceType(surf) === 'menu' || surfaceType(surf) === 'context-menu' ||\n        surfaceType(surf) === 'popover' || surfaceType(surf) === 'command')) {\n      const forward = e.key === 'ArrowDown';\n      const backward = e.key === 'ArrowUp';\n      if (forward || backward) {\n        e.preventDefault();\n        const groupId = opt.getAttribute('data-arcane-group');\n        const items = Array.prototype.slice.call(surf.querySelectorAll(\n          '[data-arcane-group=\"' + cssEscape(groupId) + '\"][data-arcane-value]:not([data-arcane-disabled=\"true\"])'\n        ));\n        const idx = items.indexOf(opt);\n        let next = forward ? idx + 1 : idx - 1;\n        if (next < 0) next = items.length - 1;\n        if (next >= items.length) next = 0;\n        if (items[next]) items[next].focus();\n        return;\n      }\n    }\n  }\n}\n\nfunction onPointerOver(e) {\n  const trigger = findActionTarget(e.target, 'data-arcane-mouseenter');\n  if (trigger) {\n    runActions(trigger.getAttribute('data-arcane-mouseenter'), { trigger: trigger, event: e });\n  }\n  const hoverTrigger = findActionTarget(e.target, 'data-arcane-hover-target');\n  if (hoverTrigger) {\n    const targetId = hoverTrigger.getAttribute('data-arcane-hover-target');\n    const surfaceType = hoverTrigger.getAttribute('data-arcane-hover-surface') || 'tooltip';\n    const delay = parseInt(hoverTrigger.getAttribute('data-arcane-hover-delay') || '120', 10);\n    if (hoverTrigger._arcaneHoverTimer) clearTimeout(hoverTrigger._arcaneHoverTimer);\n    hoverTrigger._arcaneHoverTimer = setTimeout(function() {\n      openSurface(surfaceType, targetId, { trigger: hoverTrigger });\n    }, delay);\n  }\n}\n\nfunction onPointerOut(e) {\n  const trigger = findActionTarget(e.target, 'data-arcane-mouseleave');\n  if (trigger && !trigger.contains(e.relatedTarget)) {\n    runActions(trigger.getAttribute('data-arcane-mouseleave'), { trigger: trigger, event: e });\n  }\n  const hoverTrigger = findActionTarget(e.target, 'data-arcane-hover-target');\n  if (hoverTrigger && !hoverTrigger.contains(e.relatedTarget)) {\n    if (hoverTrigger._arcaneHoverTimer) clearTimeout(hoverTrigger._arcaneHoverTimer);\n    const targetId = hoverTrigger.getAttribute('data-arcane-hover-target');\n    const surfaceType = hoverTrigger.getAttribute('data-arcane-hover-surface') || 'tooltip';\n    const delay = parseInt(hoverTrigger.getAttribute('data-arcane-hover-close-delay') || '60', 10);\n    hoverTrigger._arcaneHoverTimer = setTimeout(function() {\n      const surf = querySurface(surfaceType, targetId);\n      if (surf && !surf.matches(':hover')) {\n        closeSurface(surfaceType, targetId);\n      }\n    }, delay);\n  }\n}\n\nfunction onFocusIn(e) {\n  const trigger = findActionTarget(e.target, 'data-arcane-focus');\n  if (trigger) {\n    runActions(trigger.getAttribute('data-arcane-focus'), { trigger: trigger, event: e });\n  }\n}\n\nfunction onFocusOut(e) {\n  const trigger = findActionTarget(e.target, 'data-arcane-blur');\n  if (trigger) {\n    runActions(trigger.getAttribute('data-arcane-blur'), { trigger: trigger, event: e });\n  }\n}\n\nARCANE.delegation = {\n  click: onDocumentClick,\n  change: onDocumentChange,\n  input: onDocumentInput,\n  submit: onDocumentSubmit,\n  dblclick: onDocumentDblClick,\n  contextmenu: onDocumentContextMenu,\n  keydown: onDocumentKeyDown,\n  pointerover: onPointerOver,\n  pointerout: onPointerOut,\n  focusin: onFocusIn,\n  focusout: onFocusOut\n};\n\nfunction bindGroupItemClicks() {\n  document.addEventListener('click', function(e) {\n    const item = findClosest(e.target, '[data-arcane-group][data-arcane-value]');\n    if (!item) return;\n    if (item.hasAttribute('data-arcane-action')) return;\n    if (item.tagName === 'INPUT') return;\n    const groupId = item.getAttribute('data-arcane-group');\n    const root = groupRoot(groupId);\n    if (!root || root === item) return;\n    const value = item.getAttribute('data-arcane-value');\n    const mode = root.getAttribute('data-arcane-group-mode') || 'single';\n    if (item.getAttribute('data-arcane-disabled') === 'true') return;\n    if (mode === 'multi') toggleGroupValue(groupId, value);\n    else setGroupValue(groupId, value);\n    if (root.getAttribute('data-arcane-group-close-surface') === 'true') {\n      const surf = withinSurface(item);\n      if (surf) closeSurface(surfaceType(surf), surfaceId(surf));\n    }\n  }, true);\n}\n\nfunction bindNativeInputs() {\n  document.addEventListener('change', function(e) {\n    const inp = e.target;\n    if (!inp || !inp.matches) return;\n    if (inp.matches('input[data-arcane-group][data-arcane-value]')) {\n      const groupId = inp.getAttribute('data-arcane-group');\n      const value = inp.getAttribute('data-arcane-value');\n      const mode = groupMode(groupId);\n      if (mode === 'multi') {\n        if (inp.checked) {\n          const cur = groupValues(groupId);\n          if (cur.indexOf(value) < 0) setGroupRawValues(groupId, cur.concat([value]));\n        } else {\n          const cur = groupValues(groupId);\n          const idx = cur.indexOf(value);\n          if (idx >= 0) {\n            const next = cur.slice();\n            next.splice(idx, 1);\n            setGroupRawValues(groupId, next);\n          }\n        }\n      } else if (inp.checked) {\n        setGroupValue(groupId, value);\n      }\n    }\n    if (inp.matches('select[data-arcane-group]')) {\n      const groupId = inp.getAttribute('data-arcane-group');\n      const mode = groupMode(groupId);\n      if (mode === 'multi') {\n        const values = [];\n        for (let i = 0; i < inp.options.length; i++) {\n          if (inp.options[i].selected) values.push(inp.options[i].value);\n        }\n        setGroupRawValues(groupId, values);\n      } else {\n        setGroupValue(groupId, inp.value);\n      }\n    }\n  }, true);\n}\n\nfunction bindOutsideTabs() {\n  document.addEventListener('click', function(e) {\n    const tab = findClosest(e.target, '[data-arcane-tab][data-arcane-tabs-group]');\n    if (!tab) return;\n    if (tab.hasAttribute('data-arcane-action')) return;\n    if (tab.getAttribute('data-arcane-disabled') === 'true') return;\n    const groupId = tab.getAttribute('data-arcane-tabs-group');\n    const tabId = tab.getAttribute('data-arcane-tab');\n    activateTab(groupId, tabId);\n  }, true);\n}\n\nfunction bindAccordions() {\n  document.addEventListener('click', function(e) {\n    const trigger = findClosest(e.target, '[data-arcane-panel-group][data-arcane-panel]');\n    if (!trigger) return;\n    if (trigger.hasAttribute('data-arcane-action')) return;\n    if (trigger.getAttribute('data-arcane-disabled') === 'true') return;\n    const groupId = trigger.getAttribute('data-arcane-panel-group');\n    const panelId = trigger.getAttribute('data-arcane-panel');\n    togglePanel(groupId, panelId);\n  }, true);\n}\n\nfunction bindCarousels() {\n  const carousels = document.querySelectorAll('[data-arcane-carousel][data-arcane-carousel-autoplay]');\n  for (let i = 0; i < carousels.length; i++) {\n    startCarouselAutoplay(carousels[i]);\n  }\n}\n\nfunction bindContextMenus() {\n  document.addEventListener('contextmenu', function(e) {\n    const trigger = findActionTarget(e.target, 'data-arcane-context-trigger');\n    if (!trigger) return;\n    e.preventDefault();\n    const targetId = trigger.getAttribute('data-arcane-context-trigger');\n    const surface = querySurface('context-menu', targetId);\n    if (!surface) return;\n    surface._arcaneContextX = e.clientX;\n    surface._arcaneContextY = e.clientY;\n    surface.style.position = 'fixed';\n    surface.style.top = e.clientY + 'px';\n    surface.style.left = e.clientX + 'px';\n    openSurface('context-menu', targetId, { trigger: trigger, skipAnchorListener: true });\n  }, true);\n}\n\nfunction hydrateInitialState() {\n  hydrateThemeFromStorage();\n  const surfaces = document.querySelectorAll('[data-arcane-surface][data-arcane-state=\"closed\"]');\n  for (let i = 0; i < surfaces.length; i++) {\n    surfaces[i].setAttribute('hidden', '');\n    surfaces[i].setAttribute('aria-hidden', 'true');\n  }\n  const opens = document.querySelectorAll('[data-arcane-surface][data-arcane-state=\"open\"]');\n  for (let i = 0; i < opens.length; i++) {\n    const el = opens[i];\n    el.removeAttribute('hidden');\n    el.setAttribute('aria-hidden', 'false');\n    ARCANE.stack.push({\n      type: el.getAttribute('data-arcane-surface'),\n      id: el.getAttribute('data-arcane-id'),\n      el: el\n    });\n  }\n}\n\nfunction arcaneInit() {\n  if (ARCANE._initialized) return;\n  ARCANE._initialized = true;\n\n  document.addEventListener('click', onDocumentClick, false);\n  document.addEventListener('change', onDocumentChange, false);\n  document.addEventListener('input', onDocumentInput, false);\n  document.addEventListener('submit', onDocumentSubmit, false);\n  document.addEventListener('dblclick', onDocumentDblClick, false);\n  document.addEventListener('contextmenu', onDocumentContextMenu, false);\n  document.addEventListener('keydown', onDocumentKeyDown, false);\n  document.addEventListener('pointerover', onPointerOver, false);\n  document.addEventListener('pointerout', onPointerOut, false);\n  document.addEventListener('focusin', onFocusIn, false);\n  document.addEventListener('focusout', onFocusOut, false);\n\n  bindGroupItemClicks();\n  bindNativeInputs();\n  bindOutsideTabs();\n  bindAccordions();\n  bindContextMenus();\n  bindCarousels();\n  bindSliders();\n  bindCommandKeyboard();\n  bindCalendars();\n  bindTimePickers();\n  hydrateInitialState();\n\n  fireEvent(document, 'arcane:ready', {});\n}\n\nif (document.readyState === 'loading') {\n  document.addEventListener('DOMContentLoaded', arcaneInit);\n} else {\n  arcaneInit();\n}\n\nARCANE.init = arcaneInit;\n\n"+"})();\n"
return new A.iX(null,(s.charCodeAt(0)==0?s:s)+"\n(function() {\n  'use strict';\n\n  if (document.readyState === 'loading') {\n    document.addEventListener('DOMContentLoaded', bindAllComponents);\n  } else {\n    setTimeout(bindAllComponents, 100);\n  }\n\n  function bindAllComponents() {\n    bindSliders();\n    bindColorInputs();\n    bindCheckboxes();\n    bindToggleSwitches();\n    bindRadioButtons();\n    bindNumberInputs();\n    bindTagInputs();\n    bindFileUploads();\n    bindMutableText();\n    bindOtpInputs();\n    bindComboboxes();\n    bindCalendars();\n    bindDatePickers();\n    bindTimePickers();\n    bindFormattedInputs();\n    bindToggleButtonGroups();\n    bindCycleButtons();\n    bindToggleButtons();\n    bindButtons();\n    bindCopyButtons();\n    bindTabs();\n    bindExpandersAccordions();\n    bindDropdowns();\n    bindSelectors();\n    bindTreeViews();\n    bindPagination();\n    bindChips();\n    bindBackToTop();\n    bindContextMenus();\n    bindMenubars();\n    bindResizables();\n    bindCommandPalettes();\n    bindSteps();\n    bindTimelines();\n    bindDotIndicators();\n    bindTrackers();\n    bindDocsToc();\n    bindToasts();\n    bindPopovers();\n    bindTooltips();\n    bindDialogs();\n    bindDrawers();\n    bindMobileMenus();\n    bindSheets();\n    bindEmailDialogs();\n    bindTimeDialogs();\n    bindItemPickers();\n    bindChatScreens();\n    bindMapDebugMode();\n    bindMapPinTooltips();\n    bindLocationListHover();\n    bindRainbowTheme();\n    bindCarousels();\n  }\n\n    // ===== SLIDERS =====\n  function bindSliders() {\n    // Regular sliders\n    document.querySelectorAll('.arcane-slider').forEach(function(slider) {\n      if (slider.dataset.arcaneInteractive) return;\n      slider.dataset.arcaneInteractive = 'true';\n\n      var input = slider.querySelector('.arcane-slider-input');\n      var track = slider.querySelector('.arcane-slider-track-fill');\n      var thumb = slider.querySelector('.arcane-slider-thumb');\n      var valueDisplay = slider.querySelector('.arcane-slider-value');\n\n      if (!input) return;\n\n      input.addEventListener('input', function() {\n        var min = parseFloat(input.min) || 0;\n        var max = parseFloat(input.max) || 100;\n        var value = parseFloat(input.value);\n        var percent = ((value - min) / (max - min)) * 100;\n\n        // Update track fill\n        if (track) track.style.width = percent + '%';\n\n        // Update thumb position\n        if (thumb) {\n          var thumbSize = parseInt(thumb.style.width) || 18;\n          thumb.style.left = 'calc(' + percent + '% - ' + (thumbSize / 2) + 'px)';\n        }\n\n        // Update value display\n        if (valueDisplay) {\n          var text = valueDisplay.textContent;\n          var prefix = text.match(/^[^\\d-]*/)?.[0] || '';\n          var suffix = text.match(/[^\\d]*$/)?.[0] || '';\n          valueDisplay.textContent = prefix + Math.round(value) + suffix;\n        }\n      });\n    });\n\n    // Range sliders\n    document.querySelectorAll('.arcane-range-slider').forEach(function(slider) {\n      if (slider.dataset.arcaneInteractive) return;\n      slider.dataset.arcaneInteractive = 'true';\n\n      var minInput = slider.querySelector('.arcane-range-slider-input-min');\n      var maxInput = slider.querySelector('.arcane-range-slider-input-max');\n      var track = slider.querySelector('.arcane-range-slider-track-fill');\n      var minThumb = slider.querySelector('.arcane-range-slider-thumb-min');\n      var maxThumb = slider.querySelector('.arcane-range-slider-thumb-max');\n      var valuesDisplay = slider.querySelector('.arcane-range-slider-values');\n\n      if (!minInput || !maxInput) return;\n\n      function updateRange() {\n        var min = parseFloat(minInput.min) || 0;\n        var max = parseFloat(minInput.max) || 100;\n        var minVal = parseFloat(minInput.value);\n        var maxVal = parseFloat(maxInput.value);\n        var minPercent = ((minVal - min) / (max - min)) * 100;\n        var maxPercent = ((maxVal - min) / (max - min)) * 100;\n\n        // Update track\n        if (track) {\n          track.style.left = minPercent + '%';\n          track.style.width = (maxPercent - minPercent) + '%';\n        }\n\n        // Update thumbs\n        var thumbSize = minThumb ? parseInt(minThumb.style.width) || 18 : 18;\n        if (minThumb) minThumb.style.left = 'calc(' + minPercent + '% - ' + (thumbSize / 2) + 'px)';\n        if (maxThumb) maxThumb.style.left = 'calc(' + maxPercent + '% - ' + (thumbSize / 2) + 'px)';\n\n        // Update display\n        if (valuesDisplay) {\n          valuesDisplay.textContent = Math.round(minVal) + ' \u2013 ' + Math.round(maxVal);\n        }\n      }\n\n      minInput.addEventListener('input', function() {\n        var maxVal = parseFloat(maxInput.value);\n        if (parseFloat(minInput.value) >= maxVal) {\n          minInput.value = maxVal - 1;\n        }\n        updateRange();\n      });\n\n      maxInput.addEventListener('input', function() {\n        var minVal = parseFloat(minInput.value);\n        if (parseFloat(maxInput.value) <= minVal) {\n          maxInput.value = minVal + 1;\n        }\n        updateRange();\n      });\n    });\n\n    // Legacy slider support (fallback for old class names)\n    document.querySelectorAll('input[type=\"range\"]:not(.arcane-slider-input):not(.arcane-range-slider-input)').forEach(function(input) {\n      if (input.dataset.arcaneInteractive) return;\n      input.dataset.arcaneInteractive = 'true';\n\n      var container = input.closest('div');\n      if (!container) return;\n\n      input.addEventListener('input', function() {\n        var min = parseFloat(input.min) || 0;\n        var max = parseFloat(input.max) || 100;\n        var value = parseFloat(input.value);\n        var percent = ((value - min) / (max - min)) * 100;\n\n        // Update any track fill\n        container.querySelectorAll('[style*=\"position: absolute\"]').forEach(function(el) {\n          if (el.style.width && el.style.width.includes('%') && el !== input) {\n            el.style.width = percent + '%';\n          }\n        });\n\n        // Update any thumb\n        container.querySelectorAll('[style*=\"border-radius: 9999px\"]').forEach(function(thumb) {\n          if (thumb.style.position === 'absolute' && thumb.style.width) {\n            var thumbSize = parseInt(thumb.style.width) || 18;\n            thumb.style.left = 'calc(' + percent + '% - ' + (thumbSize / 2) + 'px)';\n          }\n        });\n      });\n    });\n  }\n\n    function bindColorInputs() {\n    document.querySelectorAll('.arcane-color-input').forEach(function(container) {\n      if (container.dataset.arcaneInteractive === 'true') return;\n      if (container.dataset.disabled === 'true') return;\n      container.dataset.arcaneInteractive = 'true';\n\n      var nativeInput = container.querySelector('.arcane-color-input-native');\n      var hexInput = container.querySelector('.arcane-color-input-hex');\n      var swatch = container.querySelector('.arcane-color-input-swatch');\n      var presets = container.querySelectorAll('.arcane-color-input-preset');\n\n      function updateColor(color) {\n        color = color.toUpperCase();\n        container.dataset.value = color;\n\n        if (swatch) {\n          swatch.style.background = color;\n        }\n\n        if (nativeInput) {\n          nativeInput.value = color;\n        }\n\n        if (hexInput) {\n          hexInput.value = color;\n        }\n\n        presets.forEach(function(preset) {\n          if (preset.dataset.color === color) {\n            preset.style.borderColor = 'var(--arcane-accent)';\n          } else {\n            preset.style.borderColor = 'var(--arcane-border)';\n          }\n        });\n      }\n\n      if (nativeInput) {\n        nativeInput.addEventListener('input', function() {\n          updateColor(nativeInput.value);\n        });\n      }\n\n      if (hexInput) {\n        hexInput.addEventListener('input', function() {\n          var val = hexInput.value.trim().toUpperCase();\n          if (!val.startsWith('#')) val = '#' + val;\n          if (/^#[0-9A-F]{6}$/.test(val)) {\n            updateColor(val);\n          }\n        });\n      }\n\n      presets.forEach(function(preset) {\n        preset.addEventListener('click', function(e) {\n          e.preventDefault();\n          var color = preset.dataset.color;\n          if (color) {\n            updateColor(color);\n          }\n        });\n      });\n    });\n  }\n\n  function bindNumberInputs() {\n    document.querySelectorAll('.arcane-number-input').forEach(function(container) {\n      if (container.dataset.arcaneInteractive === 'true') return;\n      if (container.dataset.disabled === 'true') return;\n      container.dataset.arcaneInteractive = 'true';\n\n      var decrementBtn = container.querySelector('.arcane-number-input-decrement');\n      var incrementBtn = container.querySelector('.arcane-number-input-increment');\n      var display = container.querySelector('.arcane-number-input-display');\n\n      function getValue() {\n        return parseFloat(container.dataset.value) || 0;\n      }\n\n      function getMin() {\n        return parseFloat(container.dataset.min) || 0;\n      }\n\n      function getMax() {\n        return parseFloat(container.dataset.max) || 100;\n      }\n\n      function getStep() {\n        return parseFloat(container.dataset.step) || 1;\n      }\n\n      function getDecimals() {\n        return parseInt(container.dataset.decimals) || 0;\n      }\n\n      function updateValue(newValue) {\n        var min = getMin();\n        var max = getMax();\n        var decimals = getDecimals();\n\n        newValue = Math.max(min, Math.min(max, newValue));\n        container.dataset.value = newValue.toString();\n\n        if (display) {\n          display.textContent = decimals > 0 ? newValue.toFixed(decimals) : Math.round(newValue).toString();\n        }\n\n        if (decrementBtn) {\n          decrementBtn.disabled = newValue <= min;\n          decrementBtn.style.cursor = newValue <= min ? 'not-allowed' : 'pointer';\n          decrementBtn.style.color = newValue <= min ? 'var(--arcane-muted)' : 'var(--arcane-on-surface)';\n        }\n        if (incrementBtn) {\n          incrementBtn.disabled = newValue >= max;\n          incrementBtn.style.cursor = newValue >= max ? 'not-allowed' : 'pointer';\n          incrementBtn.style.color = newValue >= max ? 'var(--arcane-muted)' : 'var(--arcane-on-surface)';\n        }\n      }\n\n      if (decrementBtn) {\n        decrementBtn.addEventListener('click', function(e) {\n          e.preventDefault();\n          if (!decrementBtn.disabled) {\n            updateValue(getValue() - getStep());\n          }\n        });\n      }\n\n      if (incrementBtn) {\n        incrementBtn.addEventListener('click', function(e) {\n          e.preventDefault();\n          if (!incrementBtn.disabled) {\n            updateValue(getValue() + getStep());\n          }\n        });\n      }\n    });\n  }\n\n  function bindCheckboxes() {\n    document.querySelectorAll('.arcane-checkbox').forEach(function(checkbox) {\n      if (checkbox.dataset.arcaneInteractive === 'true') return;\n      if (checkbox.dataset.disabled === 'true') return;\n      checkbox.dataset.arcaneInteractive = 'true';\n\n      var box = checkbox.querySelector('.arcane-checkbox-box');\n      if (!box) return;\n\n      checkbox.addEventListener('click', function(e) {\n        e.preventDefault();\n        var isChecked = checkbox.dataset.checked === 'true';\n        var newState = !isChecked;\n        checkbox.dataset.checked = newState.toString();\n\n        if (newState) {\n          box.style.background = 'var(--arcane-success)';\n          box.style.borderColor = 'var(--arcane-success)';\n          box.innerHTML = '<span style=\"color: var(--arcane-success-foreground); font-size: 12px; font-weight: bold;\">\u2713</span>';\n        } else {\n          box.style.background = 'transparent';\n          box.style.borderColor = 'var(--arcane-border)';\n          box.innerHTML = '';\n        }\n      });\n    });\n  }\n\n  function bindToggleSwitches() {\n    document.querySelectorAll('.arcane-toggle-switch').forEach(function(toggle) {\n      if (toggle.dataset.arcaneInteractive === 'true') return;\n      if (toggle.dataset.disabled === 'true') return;\n      toggle.dataset.arcaneInteractive = 'true';\n\n      var track = toggle.querySelector('.arcane-toggle-switch-track');\n      var thumb = toggle.querySelector('.arcane-toggle-switch-thumb');\n      if (!track || !thumb) return;\n\n      toggle.addEventListener('click', function(e) {\n        e.preventDefault();\n        var isOn = toggle.dataset.checked === 'true';\n        var newState = !isOn;\n        toggle.dataset.checked = newState.toString();\n\n        if (newState) {\n          track.style.background = 'var(--arcane-success)';\n          thumb.style.transform = 'translateX(20px)';\n        } else {\n          track.style.background = 'var(--arcane-surface-variant)';\n          thumb.style.transform = 'translateX(0)';\n        }\n      });\n    });\n  }\n\n  function bindRadioButtons() {\n    document.querySelectorAll('.arcane-radio-group').forEach(function(group) {\n      if (group.dataset.arcaneInteractive === 'true') return;\n      group.dataset.arcaneInteractive = 'true';\n\n      var radios = group.querySelectorAll('.arcane-radio-item, .arcane-radio-card, .arcane-radio-button, .arcane-radio-chip, .arcane-radio');\n\n      radios.forEach(function(radio) {\n        var input = radio.querySelector('input[type=\"radio\"]');\n        if (!input || input.disabled) return;\n\n        radio.addEventListener('click', function(e) {\n          if (e.target === input) return;\n          e.preventDefault();\n\n          input.checked = true;\n          input.dispatchEvent(new Event('change', { bubbles: true }));\n\n          radios.forEach(function(r) {\n            var rInput = r.querySelector('input[type=\"radio\"]');\n            var isChecked = rInput && rInput.checked;\n\n            var circle = r.querySelector('.arcane-radio-circle');\n            if (circle) {\n              circle.style.borderColor = isChecked ? 'var(--arcane-accent)' : 'var(--arcane-border)';\n              var dot = circle.querySelector('div');\n              if (isChecked && !dot) {\n                dot = document.createElement('div');\n                dot.style.cssText = 'width: 10px; height: 10px; border-radius: 50%; background: var(--arcane-accent);';\n                circle.appendChild(dot);\n              } else if (!isChecked && dot) {\n                dot.remove();\n              }\n            }\n\n            if (r.classList.contains('arcane-radio-card')) {\n              r.style.borderColor = isChecked ? 'var(--arcane-accent)' : 'var(--arcane-border)';\n              r.style.borderWidth = isChecked ? '2px' : '1px';\n              r.style.background = isChecked ? 'var(--arcane-accent-container)' : 'var(--arcane-surface)';\n            }\n\n            if (r.classList.contains('arcane-radio-button')) {\n              r.style.background = isChecked ? 'var(--arcane-accent)' : 'var(--arcane-surface)';\n              r.style.color = isChecked ? 'var(--arcane-accent-foreground)' : 'var(--arcane-on-surface)';\n              r.style.borderColor = isChecked ? 'var(--arcane-accent)' : 'var(--arcane-border)';\n            }\n\n            if (r.classList.contains('arcane-radio-chip')) {\n              r.style.background = isChecked ? 'var(--arcane-accent-container)' : 'var(--arcane-surface)';\n              r.style.color = isChecked ? 'var(--arcane-accent)' : 'var(--arcane-on-surface)';\n              r.style.borderColor = isChecked ? 'var(--arcane-accent)' : 'var(--arcane-border)';\n            }\n\n            var indicator = r.querySelector('.arcane-radio-indicator');\n            if (indicator) {\n              indicator.style.borderColor = isChecked ? 'var(--arcane-accent)' : 'var(--arcane-border)';\n              if (isChecked) {\n                indicator.innerHTML = '<div style=\"width: 10px; height: 10px; border-radius: 9999px; background: var(--arcane-accent);\"></div>';\n              } else {\n                indicator.innerHTML = '';\n              }\n            }\n          });\n        });\n      });\n    });\n  }\n\n  function bindMutableText() {\n    document.querySelectorAll('.arcane-mutable-text').forEach(function(container) {\n      if (container.dataset.arcaneInteractive === 'true') return;\n      container.dataset.arcaneInteractive = 'true';\n\n      var display = container.querySelector('.arcane-mutable-display');\n      var editContainer = container.querySelector('.arcane-mutable-edit');\n      var input = container.querySelector('.arcane-mutable-input');\n      var saveBtn = container.querySelector('.arcane-mutable-save');\n      var cancelBtn = container.querySelector('.arcane-mutable-cancel');\n      var charCounter = container.querySelector('.arcane-mutable-counter');\n\n      if (!display || !editContainer || !input) return;\n\n      var trigger = container.dataset.trigger || 'click';\n      var originalValue = '';\n\n      function showEdit() {\n        originalValue = display.textContent.trim();\n        input.value = originalValue;\n        display.style.display = 'none';\n        editContainer.style.display = 'flex';\n        input.focus();\n        input.select();\n        updateCharCounter();\n      }\n\n      function hideEdit(save) {\n        if (save) {\n          var newValue = input.value.trim();\n          var minLength = parseInt(container.dataset.minLength) || 0;\n          var maxLength = parseInt(container.dataset.maxLength) || Infinity;\n\n          if (newValue.length < minLength || newValue.length > maxLength) {\n            input.style.borderColor = 'var(--arcane-error)';\n            return;\n          }\n\n          display.textContent = newValue || originalValue;\n          container.dataset.value = newValue;\n        }\n        display.style.display = '';\n        editContainer.style.display = 'none';\n        input.style.borderColor = '';\n      }\n\n      function updateCharCounter() {\n        if (!charCounter) return;\n        var maxLength = parseInt(container.dataset.maxLength) || 0;\n        if (maxLength > 0) {\n          charCounter.textContent = input.value.length + '/' + maxLength;\n          charCounter.style.color = input.value.length > maxLength ? 'var(--arcane-error)' : 'var(--arcane-muted)';\n        }\n      }\n\n      if (trigger === 'click' || trigger === 'doubleClick') {\n        var eventType = trigger === 'doubleClick' ? 'dblclick' : 'click';\n        display.addEventListener(eventType, function(e) {\n          e.preventDefault();\n          showEdit();\n        });\n      } else if (trigger === 'hover') {\n        var hoverTimer = null;\n        display.addEventListener('mouseenter', function() {\n          hoverTimer = setTimeout(showEdit, 500);\n        });\n        display.addEventListener('mouseleave', function() {\n          if (hoverTimer) clearTimeout(hoverTimer);\n        });\n      }\n\n      var editIcon = container.querySelector('.arcane-mutable-edit-icon');\n      if (editIcon) {\n        editIcon.addEventListener('click', function(e) {\n          e.stopPropagation();\n          showEdit();\n        });\n      }\n\n      input.addEventListener('keydown', function(e) {\n        if (e.key === 'Enter' && !e.shiftKey) {\n          e.preventDefault();\n          hideEdit(true);\n        } else if (e.key === 'Escape') {\n          e.preventDefault();\n          hideEdit(false);\n        }\n      });\n\n      input.addEventListener('input', updateCharCounter);\n\n      if (saveBtn) {\n        saveBtn.addEventListener('click', function(e) {\n          e.preventDefault();\n          hideEdit(true);\n        });\n      }\n\n      if (cancelBtn) {\n        cancelBtn.addEventListener('click', function(e) {\n          e.preventDefault();\n          hideEdit(false);\n        });\n      }\n\n      document.addEventListener('click', function(e) {\n        if (editContainer.style.display !== 'none' && !container.contains(e.target)) {\n          hideEdit(false);\n        }\n      });\n    });\n  }\n\n  function bindTagInputs() {\n    document.querySelectorAll('.arcane-tag-input').forEach(function(container) {\n      if (container.dataset.arcaneInteractive === 'true') return;\n      container.dataset.arcaneInteractive = 'true';\n\n      var input = container.querySelector('.arcane-tag-input-field');\n      var tagsContainer = container.querySelector('.arcane-tag-input-tags');\n      if (!input || !tagsContainer) return;\n\n      container.querySelectorAll('.arcane-tag-remove').forEach(function(btn) {\n        btn.addEventListener('click', function(e) {\n          e.preventDefault();\n          var tag = btn.closest('.arcane-tag');\n          if (tag) tag.remove();\n        });\n      });\n\n      input.addEventListener('keydown', function(e) {\n        if (e.key === 'Enter' || e.key === ',') {\n          e.preventDefault();\n          var value = input.value.trim();\n          if (!value) return;\n\n          var tag = document.createElement('span');\n          tag.className = 'arcane-tag';\n          tag.style.cssText = 'display: inline-flex; align-items: center; gap: 4px; padding: 4px 8px; background: var(--arcane-accent); color: var(--arcane-accent-foreground); border-radius: 9999px; font-size: 0.75rem;';\n          tag.innerHTML = value + '<button type=\"button\" class=\"arcane-tag-remove\" style=\"display: flex; width: 14px; height: 14px; padding: 0; border: none; background: rgba(255,255,255,0.2); border-radius: 9999px; color: inherit; cursor: pointer; align-items: center; justify-content: center;\">\xd7</button>';\n\n          tag.querySelector('.arcane-tag-remove').addEventListener('click', function() { tag.remove(); });\n          tagsContainer.appendChild(tag);\n          input.value = '';\n        }\n      });\n    });\n  }\n\n  function bindFileUploads() {\n    document.querySelectorAll('.arcane-file-upload').forEach(function(dropzone) {\n      if (dropzone.dataset.arcaneInteractive === 'true') return;\n      dropzone.dataset.arcaneInteractive = 'true';\n\n      var fileInput = dropzone.querySelector('.arcane-file-input');\n      if (!fileInput) return;\n\n      dropzone.addEventListener('click', function(e) {\n        if (e.target !== fileInput) {\n          fileInput.click();\n        }\n      });\n\n      fileInput.addEventListener('change', function() {\n        var files = fileInput.files;\n        if (!files || files.length === 0) return;\n\n        var fileList = dropzone.querySelector('.arcane-file-list');\n        if (fileList) {\n          fileList.innerHTML = '';\n          for (var i = 0; i < files.length; i++) {\n            var file = files[i];\n            var size = file.size < 1024 ? file.size + ' B' :\n                       file.size < 1024 * 1024 ? (file.size / 1024).toFixed(1) + ' KB' :\n                       (file.size / (1024 * 1024)).toFixed(1) + ' MB';\n\n            var item = document.createElement('div');\n            item.style.cssText = 'display: flex; align-items: center; justify-content: space-between; padding: 8px; background: var(--arcane-surface-variant); border-radius: 4px; font-size: 0.875rem;';\n            item.innerHTML = '<span>' + file.name + '</span><span style=\"color: var(--arcane-muted);\">' + size + '</span>';\n            fileList.appendChild(item);\n          }\n        }\n      });\n\n      dropzone.addEventListener('dragover', function(e) {\n        e.preventDefault();\n        dropzone.style.borderColor = 'var(--arcane-accent)';\n      });\n\n      dropzone.addEventListener('dragleave', function() {\n        dropzone.style.borderColor = 'var(--arcane-border)';\n      });\n\n      dropzone.addEventListener('drop', function(e) {\n        e.preventDefault();\n        dropzone.style.borderColor = 'var(--arcane-border)';\n        if (e.dataTransfer?.files?.length > 0) {\n          fileInput.files = e.dataTransfer.files;\n          fileInput.dispatchEvent(new Event('change'));\n        }\n      });\n    });\n  }\n\n  function bindOtpInputs() {\n    document.querySelectorAll('.arcane-otp-input').forEach(function(container) {\n      if (container.dataset.arcaneInteractive === 'true') return;\n      container.dataset.arcaneInteractive = 'true';\n\n      var inputs = container.querySelectorAll('.arcane-otp-digit');\n      if (!inputs.length) return;\n\n      inputs.forEach(function(input, index) {\n        input.addEventListener('input', function(e) {\n          var value = input.value;\n\n          if (value.length > 1) {\n            var digits = value.replace(/[^0-9]/g, '').split('');\n            inputs.forEach(function(inp, i) {\n              if (digits[i]) inp.value = digits[i];\n            });\n            var lastIndex = Math.min(digits.length, inputs.length) - 1;\n            if (lastIndex >= 0) inputs[lastIndex].focus();\n            return;\n          }\n\n          if (value && index < inputs.length - 1) {\n            inputs[index + 1].focus();\n          }\n        });\n\n        input.addEventListener('keydown', function(e) {\n          if (e.key === 'Backspace' && !input.value && index > 0) {\n            inputs[index - 1].focus();\n          } else if (e.key === 'ArrowLeft' && index > 0) {\n            inputs[index - 1].focus();\n          } else if (e.key === 'ArrowRight' && index < inputs.length - 1) {\n            inputs[index + 1].focus();\n          }\n        });\n\n        input.addEventListener('focus', function() {\n          input.select();\n        });\n      });\n    });\n  }\n\n  function bindComboboxes() {\n    document.querySelectorAll('.arcane-combobox').forEach(function(container) {\n      if (container.dataset.arcaneInteractive === 'true') return;\n      container.dataset.arcaneInteractive = 'true';\n\n      var trigger = container.querySelector('.arcane-combobox-trigger');\n      var dropdown = container.querySelector('.arcane-combobox-dropdown');\n      var searchInput = container.querySelector('.arcane-combobox-search');\n      var options = container.querySelectorAll('.arcane-combobox-option');\n\n      if (!trigger) return;\n\n      var isOpen = false;\n      var selectedIndex = -1;\n\n      function openDropdown() {\n        if (dropdown) {\n          dropdown.style.display = 'block';\n          isOpen = true;\n          container.classList.add('open');\n          if (searchInput) searchInput.focus();\n        }\n      }\n\n      function closeDropdown() {\n        if (dropdown) {\n          dropdown.style.display = 'none';\n          isOpen = false;\n          container.classList.remove('open');\n          selectedIndex = -1;\n        }\n      }\n\n      function updateHighlight() {\n        options.forEach(function(opt, i) {\n          opt.style.backgroundColor = i === selectedIndex ? 'var(--arcane-surface-variant)' : '';\n        });\n      }\n\n      trigger.addEventListener('click', function(e) {\n        e.stopPropagation();\n        if (isOpen) closeDropdown();\n        else openDropdown();\n      });\n\n      if (searchInput) {\n        searchInput.addEventListener('input', function() {\n          var query = searchInput.value.toLowerCase();\n          options.forEach(function(opt) {\n            var label = opt.textContent.toLowerCase();\n            opt.style.display = label.includes(query) ? '' : 'none';\n          });\n        });\n      }\n\n      container.addEventListener('keydown', function(e) {\n        if (!isOpen) return;\n\n        if (e.key === 'ArrowDown') {\n          e.preventDefault();\n          selectedIndex = Math.min(selectedIndex + 1, options.length - 1);\n          updateHighlight();\n        } else if (e.key === 'ArrowUp') {\n          e.preventDefault();\n          selectedIndex = Math.max(selectedIndex - 1, 0);\n          updateHighlight();\n        } else if (e.key === 'Enter' && selectedIndex >= 0) {\n          e.preventDefault();\n          options[selectedIndex].click();\n        } else if (e.key === 'Escape') {\n          closeDropdown();\n        }\n      });\n\n      options.forEach(function(opt) {\n        opt.addEventListener('click', function() {\n          var label = opt.querySelector('div > div')?.textContent || opt.textContent;\n          var valueSpan = trigger.querySelector('span');\n          if (valueSpan) {\n            valueSpan.textContent = label;\n            valueSpan.style.color = 'var(--arcane-on-surface)';\n          }\n          closeDropdown();\n        });\n      });\n\n      document.addEventListener('click', function(e) {\n        if (!container.contains(e.target)) closeDropdown();\n      });\n    });\n  }\n\n  function bindCalendars() {\n    document.querySelectorAll('.arcane-calendar').forEach(function(calendar) {\n      if (calendar.dataset.arcaneInteractive === 'true') return;\n      calendar.dataset.arcaneInteractive = 'true';\n\n      var days = calendar.querySelectorAll('.arcane-calendar-day:not(.disabled):not(.outside)');\n      days.forEach(function(day) {\n        day.addEventListener('click', function() {\n          calendar.querySelectorAll('.arcane-calendar-day.selected').forEach(function(d) {\n            d.classList.remove('selected');\n            d.style.background = 'transparent';\n            d.style.color = 'var(--arcane-on-surface)';\n          });\n\n          day.classList.add('selected');\n          day.style.background = 'var(--arcane-accent)';\n          day.style.color = 'var(--arcane-on-accent)';\n        });\n      });\n    });\n  }\n\n  function bindDatePickers() {\n    document.querySelectorAll('.arcane-date-picker').forEach(function(container) {\n      if (container.dataset.arcaneInteractive === 'true') return;\n      container.dataset.arcaneInteractive = 'true';\n\n      var trigger = container.querySelector('.arcane-date-picker-trigger');\n      var dropdown = container.querySelector('.arcane-date-picker-dropdown');\n      var clearBtn = container.querySelector('.arcane-date-picker-clear');\n\n      if (!trigger) return;\n\n      var isOpen = false;\n\n      function toggleDropdown() {\n        isOpen = !isOpen;\n        if (dropdown) dropdown.style.display = isOpen ? 'block' : 'none';\n        container.classList.toggle('open', isOpen);\n      }\n\n      trigger.addEventListener('click', function(e) {\n        if (e.target.closest('.arcane-date-picker-clear')) return;\n        e.stopPropagation();\n        toggleDropdown();\n      });\n\n      if (clearBtn) {\n        clearBtn.addEventListener('click', function(e) {\n          e.stopPropagation();\n          var valueSpan = trigger.querySelector('span:nth-child(2)');\n          if (valueSpan) {\n            valueSpan.textContent = container.dataset.placeholder || 'Select date...';\n            valueSpan.style.color = 'var(--arcane-muted)';\n          }\n        });\n      }\n\n      document.addEventListener('click', function(e) {\n        if (!container.contains(e.target) && isOpen) {\n          isOpen = false;\n          if (dropdown) dropdown.style.display = 'none';\n          container.classList.remove('open');\n        }\n      });\n    });\n  }\n\n  function bindTimePickers() {\n    document.querySelectorAll('.arcane-time-picker').forEach(function(picker) {\n      if (picker.dataset.arcaneInteractive === 'true') return;\n      picker.dataset.arcaneInteractive = 'true';\n\n      var hourCol = picker.querySelector('.arcane-time-hour-column');\n      var minuteCol = picker.querySelector('.arcane-time-minute-column');\n      var periodCol = picker.querySelector('.arcane-time-period-column');\n\n      // Bind hour selection\n      if (hourCol) {\n        hourCol.querySelectorAll('.arcane-time-option').forEach(function(option) {\n          option.addEventListener('click', function() {\n            hourCol.querySelectorAll('.arcane-time-option').forEach(function(opt) {\n              opt.classList.remove('selected');\n              opt.style.background = 'transparent';\n              opt.style.color = 'var(--arcane-on-surface)';\n            });\n            option.classList.add('selected');\n            option.style.background = 'var(--arcane-accent)';\n            option.style.color = 'var(--arcane-accent-foreground)';\n            picker.dataset.hour = option.dataset.value;\n          });\n        });\n      }\n\n      // Bind minute selection\n      if (minuteCol) {\n        minuteCol.querySelectorAll('.arcane-time-option').forEach(function(option) {\n          option.addEventListener('click', function() {\n            minuteCol.querySelectorAll('.arcane-time-option').forEach(function(opt) {\n              opt.classList.remove('selected');\n              opt.style.background = 'transparent';\n              opt.style.color = 'var(--arcane-on-surface)';\n            });\n            option.classList.add('selected');\n            option.style.background = 'var(--arcane-accent)';\n            option.style.color = 'var(--arcane-accent-foreground)';\n            picker.dataset.minute = option.dataset.value;\n          });\n        });\n      }\n\n      // Bind AM/PM selection\n      if (periodCol) {\n        periodCol.querySelectorAll('.arcane-time-period-btn').forEach(function(btn) {\n          btn.addEventListener('click', function() {\n            periodCol.querySelectorAll('.arcane-time-period-btn').forEach(function(b) {\n              b.classList.remove('selected');\n              b.style.background = 'transparent';\n              b.style.color = 'var(--arcane-muted)';\n            });\n            btn.classList.add('selected');\n            btn.style.background = 'var(--arcane-accent)';\n            btn.style.color = 'var(--arcane-accent-foreground)';\n            picker.dataset.period = btn.dataset.value;\n          });\n        });\n      }\n    });\n  }\n\n  function bindFormattedInputs() {\n    document.querySelectorAll('.arcane-formatted-input').forEach(function(container) {\n      if (container.dataset.arcaneInteractive === 'true') return;\n      if (container.classList.contains('disabled')) return;\n      container.dataset.arcaneInteractive = 'true';\n\n      var segments = container.querySelectorAll('.arcane-formatted-input-segment input');\n\n      segments.forEach(function(input, index) {\n        // Auto-advance to next field when max length reached\n        input.addEventListener('input', function(e) {\n          var maxLen = parseInt(input.getAttribute('maxlength') || '0');\n          if (maxLen > 0 && input.value.length >= maxLen) {\n            // Move to next input\n            if (index < segments.length - 1) {\n              segments[index + 1].focus();\n              segments[index + 1].select();\n            }\n          }\n\n          // Dispatch change event on container\n          container.dispatchEvent(new CustomEvent('formattedchange', {\n            detail: { values: getFormattedValues() }\n          }));\n        });\n\n        // Handle backspace to go to previous field\n        input.addEventListener('keydown', function(e) {\n          if (e.key === 'Backspace' && input.value === '' && index > 0) {\n            segments[index - 1].focus();\n          }\n          // Allow arrow key navigation\n          if (e.key === 'ArrowLeft' && input.selectionStart === 0 && index > 0) {\n            segments[index - 1].focus();\n          }\n          if (e.key === 'ArrowRight' && input.selectionStart === input.value.length && index < segments.length - 1) {\n            segments[index + 1].focus();\n          }\n        });\n      });\n\n      function getFormattedValues() {\n        var values = [];\n        segments.forEach(function(seg) {\n          values.push(seg.value);\n        });\n        return values;\n      }\n    });\n  }\n\n\n    function bindToggleButtonGroups() {\n    document.querySelectorAll('.arcane-toggle-button-group').forEach(function(group) {\n      if (group.dataset.arcaneInteractive === 'true') return;\n      group.dataset.arcaneInteractive = 'true';\n\n      var buttons = group.querySelectorAll('button');\n      buttons.forEach(function(btn) {\n        btn.addEventListener('click', function(e) {\n          e.preventDefault();\n\n          buttons.forEach(function(b) {\n            b.style.backgroundColor = 'transparent';\n            b.style.color = 'var(--arcane-on-surface-variant)';\n            b.style.boxShadow = 'none';\n          });\n\n          btn.style.backgroundColor = 'var(--arcane-surface)';\n          btn.style.color = 'var(--arcane-on-surface)';\n          btn.style.boxShadow = 'var(--arcane-shadow-sm)';\n        });\n      });\n    });\n  }\n\n  function bindCycleButtons() {\n    document.querySelectorAll('.arcane-cycle-button').forEach(function(btn) {\n      if (btn.dataset.arcaneInteractive === 'true') return;\n      if (btn.disabled) return;\n      btn.dataset.arcaneInteractive = 'true';\n\n      btn.addEventListener('click', function(e) {\n        e.preventDefault();\n\n        var optionsStr = btn.dataset.options || '';\n        var options = optionsStr.split('|').filter(function(o) { return o.length > 0; });\n        var currentIndex = parseInt(btn.dataset.index) || 0;\n\n        if (options.length > 0) {\n          var nextIndex = (currentIndex + 1) % options.length;\n          btn.dataset.index = nextIndex.toString();\n\n          var labelSpan = btn.querySelector('.arcane-cycle-button-label');\n          if (labelSpan) {\n            labelSpan.textContent = options[nextIndex];\n          }\n        }\n\n        btn.style.transform = 'scale(0.95)';\n        setTimeout(function() {\n          btn.style.transform = 'scale(1)';\n        }, 100);\n\n        var indicator = btn.querySelector('.arcane-cycle-button-indicator');\n        if (indicator) {\n          indicator.style.transition = 'transform 0.3s ease';\n          indicator.style.transform = 'rotate(360deg)';\n          setTimeout(function() {\n            indicator.style.transition = 'none';\n            indicator.style.transform = 'rotate(0deg)';\n          }, 300);\n        }\n      });\n    });\n  }\n\n  function bindToggleButtons() {\n    document.querySelectorAll('.arcane-toggle-button').forEach(function(btn) {\n      if (btn.dataset.arcaneInteractive === 'true') return;\n      if (btn.disabled) return;\n      if (btn.closest('.arcane-toggle-button-group')) return;\n      btn.dataset.arcaneInteractive = 'true';\n\n      btn.addEventListener('click', function(e) {\n        e.preventDefault();\n        var isPressed = btn.getAttribute('aria-pressed') === 'true';\n        var newState = !isPressed;\n\n        btn.setAttribute('aria-pressed', newState.toString());\n        btn.classList.toggle('active', newState);\n\n        if (newState) {\n          btn.style.backgroundColor = 'var(--arcane-accent)';\n          btn.style.color = 'var(--arcane-accent-foreground)';\n          btn.style.border = 'none';\n        } else {\n          btn.style.backgroundColor = 'transparent';\n          btn.style.color = 'var(--arcane-on-surface)';\n          btn.style.border = '1px solid var(--arcane-border)';\n        }\n      });\n    });\n  }\n\n  function bindButtons() {\n    document.querySelectorAll('.arcane-button').forEach(function(btn) {\n      if (btn.dataset.arcaneButtonInteractive === 'true') return;\n      btn.dataset.arcaneButtonInteractive = 'true';\n\n      btn.addEventListener('mousedown', function() { btn.style.transform = 'scale(0.98)'; });\n      btn.addEventListener('mouseup', function() { btn.style.transform = 'scale(1)'; });\n      btn.addEventListener('mouseleave', function() { btn.style.transform = 'scale(1)'; });\n    });\n  }\n\n  // ===== COPY BUTTONS =====\n  function bindCopyButtons() {\n    document.querySelectorAll('[data-code]').forEach(function(btn) {\n      if (btn.dataset.arcaneCopyBound) return;\n      btn.dataset.arcaneCopyBound = 'true';\n\n      btn.addEventListener('click', function(e) {\n        e.preventDefault();\n        var code = this.dataset.code;\n        if (!code) return;\n\n        navigator.clipboard.writeText(code).then(function() {\n          // Find the icon inside the button and swap it\n          var icon = btn.querySelector('svg');\n          if (icon) {\n            var originalPath = icon.innerHTML;\n            // Set to checkmark icon\n            icon.innerHTML = '<polyline points=\"20 6 9 17 4 12\" stroke=\"currentColor\" stroke-width=\"2\" fill=\"none\" stroke-linecap=\"round\" stroke-linejoin=\"round\"></polyline>';\n            btn.style.color = 'var(--arcane-success)';\n\n            setTimeout(function() {\n              icon.innerHTML = originalPath;\n              btn.style.color = '';\n            }, 2000);\n          }\n        }).catch(function(err) {\n          console.warn('[Arcane] Clipboard write failed:', err);\n        });\n      });\n    });\n  }\n\n\n    function bindTabs() {\n    document.querySelectorAll('[style*=\"border-bottom: 2px\"]').forEach(function(tab) {\n      var container = tab.closest('[style*=\"display: flex\"][style*=\"gap\"]');\n      if (!container || container.dataset.arcaneTabsInteractive) return;\n      container.dataset.arcaneTabsInteractive = 'true';\n\n      var tabs = container.querySelectorAll('[style*=\"border-bottom\"], [style*=\"cursor: pointer\"][style*=\"padding\"]');\n      tabs.forEach(function(t) {\n        t.addEventListener('click', function() {\n          tabs.forEach(function(other) {\n            other.style.borderBottom = '2px solid transparent';\n            other.style.color = 'var(--arcane-on-surface-variant)';\n          });\n          t.style.borderBottom = '2px solid var(--arcane-accent)';\n          t.style.color = 'var(--arcane-on-surface)';\n        });\n      });\n    });\n  }\n\n  function bindExpandersAccordions() {\n    document.querySelectorAll('.arcane-expander-header, .arcane-accordion-header, button[aria-expanded]').forEach(function(header) {\n      if (header.dataset.arcaneInteractive) return;\n      header.dataset.arcaneInteractive = 'true';\n\n      header.addEventListener('click', function() {\n        var isExpanded = header.getAttribute('aria-expanded') === 'true';\n        header.setAttribute('aria-expanded', (!isExpanded).toString());\n\n        var container = header.closest('.arcane-expander, .arcane-accordion, [style*=\"border-radius\"]');\n        var content = header.nextElementSibling;\n        if (content) {\n          content.style.display = isExpanded ? 'none' : 'block';\n        }\n\n        var icon = header.querySelector('[style*=\"transform\"], svg');\n        if (icon) {\n          icon.style.transform = isExpanded ? 'rotate(0deg)' : 'rotate(180deg)';\n        }\n      });\n    });\n  }\n\n  function bindDropdowns() {\n    document.querySelectorAll('.arcane-dropdown').forEach(function(dropdown) {\n      var trigger = dropdown.querySelector('.arcane-dropdown-trigger');\n      var menu = dropdown.querySelector('.arcane-dropdown-menu');\n      if (!trigger || dropdown.dataset.arcaneInteractive) return;\n      dropdown.dataset.arcaneInteractive = 'true';\n\n      trigger.addEventListener('click', function(e) {\n        e.stopPropagation();\n        var isOpen = dropdown.classList.contains('open');\n\n        document.querySelectorAll('.arcane-dropdown.open').forEach(function(d) {\n          d.classList.remove('open');\n          var m = d.querySelector('.arcane-dropdown-menu');\n          if (m) m.style.display = 'none';\n        });\n\n        if (!isOpen && menu) {\n          dropdown.classList.add('open');\n          menu.style.display = 'block';\n        }\n      });\n\n      document.addEventListener('click', function() {\n        dropdown.classList.remove('open');\n        if (menu) menu.style.display = 'none';\n      });\n    });\n  }\n\n  function bindSelectors() {\n    document.querySelectorAll('.arcane-selector').forEach(function(selector) {\n      if (selector.dataset.arcaneInteractive) return;\n      selector.dataset.arcaneInteractive = 'true';\n\n      var wrapper = selector.closest('.arcane-selector-wrapper');\n      if (!wrapper) return;\n\n      selector.addEventListener('click', function(e) {\n        e.stopPropagation();\n        var dropdown = wrapper.querySelector('.arcane-selector-dropdown');\n        var arrow = selector.querySelector('[style*=\"transform\"]');\n\n        if (dropdown) {\n          var isOpen = dropdown.style.display !== 'none';\n          dropdown.style.display = isOpen ? 'none' : 'block';\n          if (arrow) arrow.style.transform = isOpen ? 'rotate(0)' : 'rotate(180deg)';\n        } else {\n          if (arrow) {\n            var isRotated = arrow.style.transform.includes('180');\n            arrow.style.transform = isRotated ? 'rotate(0)' : 'rotate(180deg)';\n          }\n        }\n      });\n\n      document.addEventListener('click', function(e) {\n        if (!wrapper.contains(e.target)) {\n          var dropdown = wrapper.querySelector('.arcane-selector-dropdown');\n          var arrow = selector.querySelector('[style*=\"transform\"]');\n          if (dropdown) dropdown.style.display = 'none';\n          if (arrow) arrow.style.transform = 'rotate(0)';\n        }\n      });\n    });\n\n    document.querySelectorAll('.arcane-selector-option').forEach(function(option) {\n      if (option.dataset.arcaneOptionInteractive) return;\n      option.dataset.arcaneOptionInteractive = 'true';\n\n      option.addEventListener('click', function() {\n        var wrapper = option.closest('.arcane-selector-wrapper');\n        var selector = wrapper?.querySelector('.arcane-selector');\n        var dropdown = wrapper?.querySelector('.arcane-selector-dropdown');\n        var label = option.querySelector('span');\n\n        if (selector && label) {\n          var valueSpan = selector.querySelector('span');\n          if (valueSpan) {\n            valueSpan.textContent = label.textContent;\n            valueSpan.style.color = 'var(--arcane-on-surface)';\n          }\n        }\n\n        wrapper?.querySelectorAll('.arcane-selector-option').forEach(function(opt) {\n          opt.style.backgroundColor = 'transparent';\n          opt.style.color = 'var(--arcane-on-surface)';\n          var check = opt.querySelector('span:last-child');\n          if (check && check.textContent === '\u2713') check.remove();\n        });\n\n        option.style.backgroundColor = 'var(--arcane-accent-container)';\n        option.style.color = 'var(--arcane-accent)';\n\n        if (dropdown) dropdown.style.display = 'none';\n        var arrow = selector?.querySelector('[style*=\"transform\"]');\n        if (arrow) arrow.style.transform = 'rotate(0)';\n      });\n    });\n  }\n\n  function bindTreeViews() {\n    document.querySelectorAll('[role=\"tree\"]').forEach(function(tree) {\n      if (tree.dataset.arcaneInteractive) return;\n      tree.dataset.arcaneInteractive = 'true';\n\n      tree.querySelectorAll('[role=\"treeitem\"]').forEach(function(item) {\n        item.addEventListener('click', function(e) {\n          e.stopPropagation();\n\n          var hasChildren = item.getAttribute('aria-expanded') !== null;\n          var isExpanded = item.getAttribute('aria-expanded') === 'true';\n          var isSelected = item.getAttribute('aria-selected') === 'true';\n\n          if (hasChildren) {\n            item.setAttribute('aria-expanded', (!isExpanded).toString());\n            var group = item.nextElementSibling;\n            if (group && group.getAttribute('role') === 'group') {\n              group.style.display = isExpanded ? 'none' : 'block';\n            }\n            var icon = item.querySelector('[style*=\"transform\"]');\n            if (icon) {\n              icon.style.transform = isExpanded ? 'rotate(0deg)' : 'rotate(90deg)';\n            }\n          }\n\n          tree.querySelectorAll('[role=\"treeitem\"]').forEach(function(other) {\n            other.setAttribute('aria-selected', 'false');\n            other.style.background = 'transparent';\n            var label = other.querySelector('span:last-child');\n            if (label) {\n              label.style.color = 'var(--arcane-on-surface)';\n              label.style.fontWeight = 'normal';\n            }\n          });\n\n          item.setAttribute('aria-selected', 'true');\n          item.style.background = 'var(--arcane-surface-variant)';\n          var label = item.querySelector('span:last-child');\n          if (label) {\n            label.style.color = 'var(--arcane-accent)';\n            label.style.fontWeight = '500';\n          }\n        });\n      });\n    });\n  }\n\n  function bindPagination() {\n    document.querySelectorAll('nav[aria-label=\"Pagination\"]').forEach(function(nav) {\n      if (nav.dataset.arcaneInteractive) return;\n      nav.dataset.arcaneInteractive = 'true';\n\n      nav.querySelectorAll('button').forEach(function(btn) {\n        btn.addEventListener('click', function() {\n          if (btn.disabled) return;\n\n          nav.querySelectorAll('button').forEach(function(b) {\n            if (b.getAttribute('aria-current') === 'page') {\n              b.removeAttribute('aria-current');\n              if (b.style.background?.includes('accent')) {\n                b.style.background = 'transparent';\n                b.style.border = '1px solid var(--arcane-border)';\n              } else {\n                b.style.background = 'var(--arcane-surface-variant)';\n              }\n              b.style.fontWeight = 'normal';\n            }\n          });\n\n          var pageNum = parseInt(btn.textContent);\n          if (!isNaN(pageNum)) {\n            btn.setAttribute('aria-current', 'page');\n            btn.style.background = 'var(--arcane-accent)';\n            btn.style.border = '1px solid var(--arcane-accent)';\n            btn.style.fontWeight = '500';\n          }\n        });\n      });\n    });\n  }\n\n  function bindChips() {\n    document.querySelectorAll('.arcane-chip').forEach(function(chip) {\n      if (chip.dataset.arcaneInteractive) return;\n      chip.dataset.arcaneInteractive = 'true';\n\n      if (chip.classList.contains('clickable')) {\n        chip.addEventListener('click', function() {\n          chip.style.opacity = chip.style.opacity === '0.7' ? '1' : '0.7';\n        });\n      }\n\n      var removeBtn = chip.querySelector('.arcane-chip-remove');\n      if (removeBtn) {\n        removeBtn.addEventListener('click', function(e) {\n          e.stopPropagation();\n          chip.style.transform = 'scale(0.9)';\n          chip.style.opacity = '0';\n          setTimeout(function() { chip.remove(); }, 150);\n        });\n      }\n    });\n  }\n\n  function bindBackToTop() {\n    document.querySelectorAll('[title=\"Back to top\"], button[style*=\"position: fixed\"][style*=\"bottom\"]').forEach(function(btn) {\n      if (btn.dataset.arcaneInteractive) return;\n      btn.dataset.arcaneInteractive = 'true';\n\n      btn.addEventListener('click', function() {\n        window.scrollTo({ top: 0, behavior: 'smooth' });\n      });\n\n      window.addEventListener('scroll', function() {\n        if (window.scrollY > 300) {\n          btn.style.opacity = '1';\n          btn.style.pointerEvents = 'auto';\n        } else {\n          btn.style.opacity = '0';\n          btn.style.pointerEvents = 'none';\n        }\n      });\n    });\n  }\n\n  function bindContextMenus() {\n    document.querySelectorAll('.arcane-context-menu-trigger').forEach(function(trigger) {\n      if (trigger.dataset.arcaneInteractive === 'true') return;\n      trigger.dataset.arcaneInteractive = 'true';\n\n      var menu = trigger.querySelector('.arcane-context-menu');\n      if (!menu) return;\n\n      trigger.addEventListener('contextmenu', function(e) {\n        e.preventDefault();\n\n        document.querySelectorAll('.arcane-context-menu').forEach(function(m) {\n          m.style.display = 'none';\n        });\n\n        menu.style.display = 'block';\n        menu.style.left = e.clientX + 'px';\n        menu.style.top = e.clientY + 'px';\n\n        var rect = menu.getBoundingClientRect();\n        if (rect.right > window.innerWidth) {\n          menu.style.left = (e.clientX - rect.width) + 'px';\n        }\n        if (rect.bottom > window.innerHeight) {\n          menu.style.top = (e.clientY - rect.height) + 'px';\n        }\n      });\n\n      menu.querySelectorAll('.arcane-context-menu-item:not(.disabled)').forEach(function(item) {\n        item.addEventListener('click', function() {\n          menu.style.display = 'none';\n        });\n      });\n    });\n\n    document.addEventListener('click', function() {\n      document.querySelectorAll('.arcane-context-menu').forEach(function(m) {\n        m.style.display = 'none';\n      });\n    });\n\n    document.addEventListener('keydown', function(e) {\n      if (e.key === 'Escape') {\n        document.querySelectorAll('.arcane-context-menu').forEach(function(m) {\n          m.style.display = 'none';\n        });\n      }\n    });\n  }\n\n  function bindMenubars() {\n    document.querySelectorAll('.arcane-menubar').forEach(function(menubar) {\n      if (menubar.dataset.arcaneInteractive === 'true') return;\n      menubar.dataset.arcaneInteractive = 'true';\n\n      var items = menubar.querySelectorAll('.arcane-menubar-item');\n      var activeItem = null;\n\n      items.forEach(function(item) {\n        var trigger = item.querySelector('.arcane-menubar-trigger');\n        var dropdown = item.querySelector('.arcane-menubar-dropdown');\n\n        if (!trigger || !dropdown) return;\n\n        trigger.addEventListener('click', function(e) {\n          e.stopPropagation();\n\n          if (activeItem === item) {\n            dropdown.style.display = 'none';\n            activeItem = null;\n          } else {\n            items.forEach(function(other) {\n              var d = other.querySelector('.arcane-menubar-dropdown');\n              if (d) d.style.display = 'none';\n            });\n\n            dropdown.style.display = 'block';\n            activeItem = item;\n          }\n        });\n\n        item.addEventListener('mouseenter', function() {\n          if (activeItem && activeItem !== item) {\n            var oldDropdown = activeItem.querySelector('.arcane-menubar-dropdown');\n            if (oldDropdown) oldDropdown.style.display = 'none';\n\n            dropdown.style.display = 'block';\n            activeItem = item;\n          }\n        });\n\n        dropdown.querySelectorAll('.arcane-menubar-menu-item:not(.disabled)').forEach(function(menuItem) {\n          menuItem.addEventListener('click', function() {\n            dropdown.style.display = 'none';\n            activeItem = null;\n          });\n        });\n      });\n\n      document.addEventListener('click', function(e) {\n        if (!menubar.contains(e.target)) {\n          items.forEach(function(item) {\n            var d = item.querySelector('.arcane-menubar-dropdown');\n            if (d) d.style.display = 'none';\n          });\n          activeItem = null;\n        }\n      });\n    });\n  }\n\n  function bindResizables() {\n    document.querySelectorAll('.arcane-resizable').forEach(function(container) {\n      if (container.dataset.arcaneInteractive === 'true') return;\n      container.dataset.arcaneInteractive = 'true';\n\n      var isHorizontal = container.dataset.direction === 'horizontal';\n      var handles = container.querySelectorAll('.arcane-resizable-handle');\n      var panels = container.querySelectorAll('.arcane-resizable-panel');\n\n      handles.forEach(function(handle, handleIndex) {\n        var isDragging = false;\n        var startPos = 0;\n        var startSizes = [];\n\n        handle.addEventListener('mousedown', function(e) {\n          e.preventDefault();\n          isDragging = true;\n          startPos = isHorizontal ? e.clientX : e.clientY;\n\n          startSizes = [];\n          panels.forEach(function(p) {\n            var rect = p.getBoundingClientRect();\n            startSizes.push(isHorizontal ? rect.width : rect.height);\n          });\n\n          container.classList.add('dragging');\n          document.body.style.cursor = isHorizontal ? 'col-resize' : 'row-resize';\n          document.body.style.userSelect = 'none';\n        });\n\n        document.addEventListener('mousemove', function(e) {\n          if (!isDragging) return;\n\n          var currentPos = isHorizontal ? e.clientX : e.clientY;\n          var delta = currentPos - startPos;\n\n          var panel1 = panels[handleIndex];\n          var panel2 = panels[handleIndex + 1];\n\n          if (panel1 && panel2) {\n            var newSize1 = startSizes[handleIndex] + delta;\n            var newSize2 = startSizes[handleIndex + 1] - delta;\n\n            var min1 = parseFloat(panel1.dataset.minSize) || 10;\n            var max1 = parseFloat(panel1.dataset.maxSize) || 90;\n            var min2 = parseFloat(panel2.dataset.minSize) || 10;\n            var max2 = parseFloat(panel2.dataset.maxSize) || 90;\n\n            var containerSize = isHorizontal ? container.clientWidth : container.clientHeight;\n            var minPx1 = containerSize * min1 / 100;\n            var maxPx1 = containerSize * max1 / 100;\n            var minPx2 = containerSize * min2 / 100;\n            var maxPx2 = containerSize * max2 / 100;\n\n            if (newSize1 >= minPx1 && newSize1 <= maxPx1 && newSize2 >= minPx2 && newSize2 <= maxPx2) {\n              panel1.style.flex = '0 0 ' + newSize1 + 'px';\n              panel2.style.flex = '0 0 ' + newSize2 + 'px';\n            }\n          }\n        });\n\n        document.addEventListener('mouseup', function() {\n          if (isDragging) {\n            isDragging = false;\n            container.classList.remove('dragging');\n            document.body.style.cursor = '';\n            document.body.style.userSelect = '';\n          }\n        });\n\n        handle.addEventListener('keydown', function(e) {\n          var step = e.shiftKey ? 50 : 10;\n          var delta = 0;\n\n          if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {\n            delta = -step;\n          } else if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {\n            delta = step;\n          }\n\n          if (delta !== 0) {\n            e.preventDefault();\n            var panel1 = panels[handleIndex];\n            var panel2 = panels[handleIndex + 1];\n\n            if (panel1 && panel2) {\n              var rect1 = panel1.getBoundingClientRect();\n              var rect2 = panel2.getBoundingClientRect();\n              var size1 = isHorizontal ? rect1.width : rect1.height;\n              var size2 = isHorizontal ? rect2.width : rect2.height;\n\n              panel1.style.flex = '0 0 ' + (size1 + delta) + 'px';\n              panel2.style.flex = '0 0 ' + (size2 - delta) + 'px';\n            }\n          }\n        });\n      });\n    });\n  }\n\n  function bindCommandPalettes() {\n    // Guard against multiple bindings\n    if (window.__arcaneCommandBound) return;\n    window.__arcaneCommandBound = true;\n\n    // Track selection state per overlay\n    var selectedIndices = new WeakMap();\n\n    // Inject hover/selected CSS styles if not already present\n    if (!document.getElementById('arcane-command-styles')) {\n      var style = document.createElement('style');\n      style.id = 'arcane-command-styles';\n      style.textContent = '\\\n        .arcane-command-item:hover:not(.disabled),\\\n        .neon-command-item:hover:not(.disabled) {\\\n          background-color: var(--accent, var(--secondary, rgba(255,255,255,0.1)));\\\n        }\\\n        .arcane-command-item.selected:not(.disabled),\\\n        .neon-command-item.selected:not(.disabled) {\\\n          background-color: var(--accent, var(--secondary, rgba(255,255,255,0.1)));\\\n          outline: 2px solid var(--ring, var(--primary, #3b82f6));\\\n          outline-offset: -2px;\\\n        }\\\n        .arcane-command-item.js-hidden,\\\n        .neon-command-item.js-hidden,\\\n        .arcane-command-group-heading.js-hidden,\\\n        .neon-command-group-heading.js-hidden {\\\n          display: none !important;\\\n        }\\\n      ';\n      document.head.appendChild(style);\n    }\n\n    // Helper to find the overlay from any target\n    function findOverlay(target) {\n      return target.closest('.arcane-command-overlay, .neon-command-overlay');\n    }\n\n    // Helper to get current visible items in an overlay\n    function getVisibleItems(overlay) {\n      var allItems = overlay.querySelectorAll('.arcane-command-item:not(.disabled):not(.js-hidden), .neon-command-item:not(.disabled):not(.js-hidden)');\n      return Array.from(allItems).filter(function(item) {\n        return item.offsetParent !== null;\n      });\n    }\n\n    // Get/set selected index for an overlay\n    function getSelectedIndex(overlay) {\n      return selectedIndices.get(overlay) || -1;\n    }\n    function setSelectedIndex(overlay, index) {\n      selectedIndices.set(overlay, index);\n    }\n\n    function updateSelection(overlay, items) {\n      var selectedIndex = getSelectedIndex(overlay);\n      overlay.querySelectorAll('.arcane-command-item, .neon-command-item').forEach(function(item) {\n        item.classList.remove('selected');\n      });\n      if (selectedIndex >= 0 && items[selectedIndex]) {\n        items[selectedIndex].classList.add('selected');\n        items[selectedIndex].scrollIntoView({ block: 'nearest' });\n      }\n    }\n\n    // Close overlay helper\n    function closeOverlay(overlay) {\n      if (!overlay) return;\n      overlay.style.display = 'none';\n      setSelectedIndex(overlay, -1);\n      // Dispatch custom event for Jaspr to handle state update\n      overlay.dispatchEvent(new CustomEvent('arcane-command-close', { bubbles: true }));\n    }\n\n    // Handle item click - navigate via data-href\n    function handleItemClick(item, overlay) {\n      var href = item.dataset.href;\n      var target = item.dataset.target;\n      if (href) {\n        if (target === '_blank') {\n          window.open(href, '_blank', 'noopener,noreferrer');\n        } else {\n          window.location.href = href;\n        }\n        closeOverlay(overlay);\n      }\n    }\n\n    // Filter items based on search query\n    function filterItems(overlay, query) {\n      var list = overlay.querySelector('.arcane-command-list, .neon-command-list');\n      if (!list) return;\n\n      var q = query.toLowerCase().trim();\n\n      // Get all children of the list (headings and items are siblings)\n      var children = Array.from(list.children);\n\n      // First pass: filter all items\n      children.forEach(function(child) {\n        if (child.classList.contains('arcane-command-item') || child.classList.contains('neon-command-item')) {\n          if (!q) {\n            child.classList.remove('js-hidden');\n            return;\n          }\n          var label = (child.dataset.label || '').toLowerCase();\n          var keywords = (child.dataset.keywords || '').toLowerCase();\n          var matches = label.includes(q) || keywords.includes(q);\n          child.classList.toggle('js-hidden', !matches);\n        }\n      });\n\n      // Second pass: hide headings that have no visible items following them\n      var currentHeading = null;\n      var hasVisibleItems = false;\n\n      children.forEach(function(child, index) {\n        var isHeading = child.classList.contains('arcane-command-group-heading') ||\n                        child.classList.contains('neon-command-group-heading');\n        var isItem = child.classList.contains('arcane-command-item') ||\n                     child.classList.contains('neon-command-item');\n\n        if (isHeading) {\n          // Before processing new heading, finalize the previous one\n          if (currentHeading) {\n            if (!q) {\n              currentHeading.classList.remove('js-hidden');\n            } else {\n              currentHeading.classList.toggle('js-hidden', !hasVisibleItems);\n            }\n          }\n          // Start tracking new heading\n          currentHeading = child;\n          hasVisibleItems = false;\n        } else if (isItem && currentHeading) {\n          // Check if this item is visible\n          if (!child.classList.contains('js-hidden')) {\n            hasVisibleItems = true;\n          }\n        }\n      });\n\n      // Finalize the last heading\n      if (currentHeading) {\n        if (!q) {\n          currentHeading.classList.remove('js-hidden');\n        } else {\n          currentHeading.classList.toggle('js-hidden', !hasVisibleItems);\n        }\n      }\n    }\n\n    // Auto-focus input when overlay appears\n    function focusCommandInput(overlay) {\n      if (!overlay) return;\n      var input = overlay.querySelector('.arcane-command-input, .neon-command-input');\n      if (input) {\n        setTimeout(function() {\n          input.focus();\n        }, 50);\n      }\n    }\n\n    // Watch for overlay appearance using MutationObserver\n    var observer = new MutationObserver(function(mutations) {\n      mutations.forEach(function(mutation) {\n        mutation.addedNodes.forEach(function(node) {\n          if (node.nodeType === 1) {\n            // Check if the added node is an overlay or contains one\n            if (node.classList && (node.classList.contains('arcane-command-overlay') ||\n                                   node.classList.contains('neon-command-overlay'))) {\n              focusCommandInput(node);\n            } else if (node.querySelector) {\n              var overlay = node.querySelector('.arcane-command-overlay, .neon-command-overlay');\n              if (overlay) {\n                focusCommandInput(overlay);\n              }\n            }\n          }\n        });\n      });\n    });\n\n    observer.observe(document.body, { childList: true, subtree: true });\n\n    // Also focus any existing overlay on page load\n    var existingOverlay = document.querySelector('.arcane-command-overlay, .neon-command-overlay');\n    if (existingOverlay && existingOverlay.style.display !== 'none') {\n      focusCommandInput(existingOverlay);\n    }\n\n    // Document-level click handler (works for dynamically rendered overlays)\n    document.addEventListener('click', function(e) {\n      var overlay = findOverlay(e.target);\n\n      // Handle item clicks\n      var item = e.target.closest('.arcane-command-item, .neon-command-item');\n      if (item && overlay && !item.classList.contains('disabled')) {\n        e.preventDefault();\n        e.stopPropagation();\n        handleItemClick(item, overlay);\n        return;\n      }\n\n      // Handle click-outside to close\n      var anyOverlay = document.querySelector('.arcane-command-overlay, .neon-command-overlay');\n      if (anyOverlay && anyOverlay.style.display !== 'none') {\n        var dialog = anyOverlay.querySelector('.arcane-command-dialog, .neon-command-dialog');\n        if (anyOverlay.dataset.commandClosable === 'true') {\n          if (!dialog || !dialog.contains(e.target)) {\n            closeOverlay(anyOverlay);\n          }\n        }\n      }\n    }, true); // Capture phase\n\n    // Document-level mouseover for hover selection\n    document.addEventListener('mouseover', function(e) {\n      var item = e.target.closest('.arcane-command-item, .neon-command-item');\n      if (!item) return;\n      var overlay = findOverlay(item);\n      if (!overlay) return;\n      if (item.classList.contains('disabled') || item.classList.contains('js-hidden')) return;\n\n      var items = getVisibleItems(overlay);\n      var idx = items.indexOf(item);\n      if (idx >= 0) {\n        setSelectedIndex(overlay, idx);\n        updateSelection(overlay, items);\n      }\n    });\n\n    // Document-level keyboard handler (works for dynamically rendered overlays)\n    document.addEventListener('keydown', function(e) {\n      var overlay = document.querySelector('.arcane-command-overlay, .neon-command-overlay');\n      if (!overlay || overlay.style.display === 'none') {\n        // Only handle Ctrl+K when no overlay is open\n        if ((e.metaKey || e.ctrlKey) && e.key === 'k') {\n          e.preventDefault();\n          e.stopPropagation();\n          var trigger = document.querySelector('[data-command-trigger]');\n          if (trigger) {\n            trigger.click();\n          }\n        }\n        return;\n      }\n\n      var items = getVisibleItems(overlay);\n      var selectedIndex = getSelectedIndex(overlay);\n\n      if (e.key === 'ArrowDown') {\n        e.preventDefault();\n        e.stopPropagation();\n        if (items.length > 0) {\n          selectedIndex = selectedIndex < 0 ? 0 : Math.min(selectedIndex + 1, items.length - 1);\n          setSelectedIndex(overlay, selectedIndex);\n          updateSelection(overlay, items);\n        }\n      } else if (e.key === 'ArrowUp') {\n        e.preventDefault();\n        e.stopPropagation();\n        if (items.length > 0) {\n          selectedIndex = Math.max(selectedIndex - 1, 0);\n          setSelectedIndex(overlay, selectedIndex);\n          updateSelection(overlay, items);\n        }\n      } else if (e.key === 'Enter') {\n        e.preventDefault();\n        e.stopPropagation();\n        if (selectedIndex >= 0 && items[selectedIndex]) {\n          handleItemClick(items[selectedIndex], overlay);\n        } else if (items.length > 0) {\n          handleItemClick(items[0], overlay);\n        }\n      } else if (e.key === 'Escape') {\n        e.preventDefault();\n        e.stopPropagation();\n        closeOverlay(overlay);\n      }\n    }, true); // Capture phase\n\n    // Document-level input handler for search filtering\n    document.addEventListener('input', function(e) {\n      var input = e.target;\n      if (!input.classList.contains('arcane-command-input') && !input.classList.contains('neon-command-input')) return;\n      var overlay = findOverlay(input);\n      if (!overlay) return;\n\n      setSelectedIndex(overlay, -1);\n      filterItems(overlay, input.value);\n    });\n  }\n\n  function bindSteps() {\n    document.querySelectorAll('.arcane-steps').forEach(function(steps) {\n      if (steps.dataset.arcaneInteractive === 'true') return;\n      steps.dataset.arcaneInteractive = 'true';\n\n      var indicators = steps.querySelectorAll('.arcane-steps-indicator:not([disabled])');\n\n      indicators.forEach(function(indicator) {\n        indicator.addEventListener('click', function() {\n          var stepIndex = indicator.dataset.stepIndex;\n          if (stepIndex === undefined) return;\n\n          var event = new CustomEvent('arcane-step-click', {\n            bubbles: true,\n            detail: { stepIndex: parseInt(stepIndex) }\n          });\n          steps.dispatchEvent(event);\n        });\n\n        indicator.addEventListener('mouseenter', function() {\n          if (!indicator.disabled) {\n            indicator.style.transform = 'scale(1.1)';\n          }\n        });\n\n        indicator.addEventListener('mouseleave', function() {\n          indicator.style.transform = 'scale(1)';\n        });\n      });\n    });\n  }\n\n  function bindTimelines() {\n    document.querySelectorAll('.arcane-timeline').forEach(function(timeline) {\n      if (timeline.dataset.arcaneInteractive === 'true') return;\n      timeline.dataset.arcaneInteractive = 'true';\n\n      var items = timeline.querySelectorAll('.arcane-timeline-item');\n\n      items.forEach(function(item) {\n        item.addEventListener('mouseenter', function() {\n          var content = item.querySelector('.arcane-timeline-content');\n          if (content) {\n            content.style.transform = 'translateX(4px)';\n            content.style.transition = 'transform 0.15s ease';\n          }\n        });\n\n        item.addEventListener('mouseleave', function() {\n          var content = item.querySelector('.arcane-timeline-content');\n          if (content) {\n            content.style.transform = 'translateX(0)';\n          }\n        });\n      });\n    });\n  }\n\n  function bindDotIndicators() {\n    // Dot indicators\n    document.querySelectorAll('.arcane-dot-indicator').forEach(function(indicator) {\n      if (indicator.dataset.arcaneInteractive === 'true') return;\n      indicator.dataset.arcaneInteractive = 'true';\n\n      var dots = indicator.querySelectorAll('.arcane-dot');\n      var currentIndex = parseInt(indicator.dataset.currentIndex || '0');\n\n      dots.forEach(function(dot, i) {\n        dot.addEventListener('click', function() {\n          if (indicator.dataset.interactive !== 'true') return;\n\n          // Update visual state\n          dots.forEach(function(d, j) {\n            var isActive = j === i;\n            d.style.background = isActive ? 'var(--arcane-accent)' : 'var(--arcane-muted)';\n            d.style.transform = isActive ? 'scale(1.2)' : 'scale(1)';\n          });\n\n          indicator.dataset.currentIndex = i.toString();\n          indicator.dispatchEvent(new CustomEvent('dotchange', { detail: { index: i } }));\n        });\n      });\n    });\n\n    // Step indicators\n    document.querySelectorAll('.arcane-step-indicator').forEach(function(indicator) {\n      if (indicator.dataset.arcaneInteractive === 'true') return;\n      indicator.dataset.arcaneInteractive = 'true';\n\n      var steps = indicator.querySelectorAll('.arcane-step');\n      var circles = indicator.querySelectorAll('.arcane-step-circle');\n      var allowNav = indicator.dataset.allowNavigation === 'true';\n\n      circles.forEach(function(circle, i) {\n        if (!allowNav) return;\n        if (circle.disabled) return;\n\n        circle.addEventListener('click', function() {\n          var currentStep = parseInt(indicator.dataset.currentStep || '0');\n          if (i > currentStep) return; // Can't skip ahead\n\n          // Update step visuals\n          circles.forEach(function(c, j) {\n            var isCompleted = j < i;\n            var isActive = j === i;\n            c.style.background = isCompleted || isActive ? 'var(--arcane-accent)' : 'var(--arcane-surface-variant)';\n            c.style.color = isCompleted || isActive ? 'var(--arcane-accent-foreground)' : 'var(--arcane-muted)';\n            c.innerHTML = isCompleted ? '\u2713' : (j + 1).toString();\n          });\n\n          indicator.dataset.currentStep = i.toString();\n          indicator.dispatchEvent(new CustomEvent('stepchange', { detail: { step: i } }));\n        });\n      });\n    });\n  }\n\n  function bindTrackers() {\n    // Grid tracker cells\n    document.querySelectorAll('.arcane-tracker').forEach(function(tracker) {\n      if (tracker.dataset.arcaneInteractive === 'true') return;\n      tracker.dataset.arcaneInteractive = 'true';\n\n      var cells = tracker.querySelectorAll('.arcane-tracker-cell');\n\n      cells.forEach(function(cell) {\n        // Show tooltip on hover\n        cell.addEventListener('mouseenter', function() {\n          cell.style.transform = 'scale(1.2)';\n          cell.style.zIndex = '1';\n        });\n\n        cell.addEventListener('mouseleave', function() {\n          cell.style.transform = 'scale(1)';\n          cell.style.zIndex = '';\n        });\n\n        // Click handler\n        cell.addEventListener('click', function() {\n          if (cell.style.cursor !== 'pointer') return;\n\n          var index = parseInt(cell.dataset.index || '0');\n          var level = cell.dataset.level || 'unknown';\n\n          tracker.dispatchEvent(new CustomEvent('celltap', {\n            detail: { index: index, level: level }\n          }));\n        });\n      });\n    });\n\n    // Uptime tracker bars\n    document.querySelectorAll('.arcane-uptime-tracker').forEach(function(tracker) {\n      if (tracker.dataset.arcaneInteractive === 'true') return;\n      tracker.dataset.arcaneInteractive = 'true';\n\n      var bars = tracker.querySelectorAll('.arcane-uptime-bar');\n\n      bars.forEach(function(bar) {\n        bar.addEventListener('mouseenter', function() {\n          bar.style.opacity = '0.8';\n        });\n\n        bar.addEventListener('mouseleave', function() {\n          bar.style.opacity = '1';\n        });\n      });\n    });\n  }\n\nfunction bindDocsToc() {\n  var tocContainer = document.querySelector('.toc-content');\n  if (!tocContainer) return;\n\n  var tocLinks = Array.prototype.slice.call(tocContainer.querySelectorAll('a'));\n\n  function normalizeHeadingLabel(raw) {\n    if (!raw) return '';\n    var label = raw.trim();\n    label = label.replace(/^#\\s*/, '');\n    label = label.replace(/\\s*#$/, '');\n    label = label.replace(/\\s+/g, ' ').trim();\n    return label;\n  }\n\n  function sanitizeExistingLabels() {\n    tocLinks.forEach(function(link) {\n      var text = link.textContent || '';\n      var normalized = normalizeHeadingLabel(text);\n      if (normalized) {\n        link.textContent = normalized;\n      }\n    });\n  }\n\n  function getHeadingId(link) {\n    var href = link.getAttribute('href');\n    if (!href) return '';\n    var hashIndex = href.indexOf('#');\n    if (hashIndex === -1) return '';\n    var rawId = href.slice(hashIndex + 1);\n    try {\n      return decodeURIComponent(rawId);\n    } catch (e) {\n      return rawId;\n    }\n  }\n\n  sanitizeExistingLabels();\n\n  if (!tocLinks.length) {\n    var headingNodes = Array.prototype.slice.call(\n      document.querySelectorAll(\n        '.prose h1[id], .prose h2[id], .prose h3[id]'\n      )\n    );\n    if (!headingNodes.length) return;\n\n    var list = tocContainer.querySelector('ul');\n    if (!list) {\n      list = document.createElement('ul');\n      tocContainer.innerHTML = '';\n      tocContainer.appendChild(list);\n    } else {\n      list.innerHTML = '';\n    }\n\n    headingNodes.forEach(function(heading) {\n      var id = heading.id;\n      if (!id) return;\n      if (heading.closest('.arcane-demo-docs')) return;\n      var headingClass = heading.className || '';\n      if (headingClass.indexOf('no_toc') !== -1) return;\n\n      var label = heading.textContent ? normalizeHeadingLabel(heading.textContent) : id;\n      if (!label) return;\n\n      var item = document.createElement('li');\n      var link = document.createElement('a');\n      link.setAttribute('href', window.location.pathname + '#' + id);\n      link.textContent = label;\n      item.appendChild(link);\n      list.appendChild(item);\n    });\n\n    tocLinks = Array.prototype.slice.call(tocContainer.querySelectorAll('a'));\n    if (!tocLinks.length) return;\n  }\n\n  sanitizeExistingLabels();\n\n  var headings = tocLinks.map(function(link) {\n    var id = getHeadingId(link);\n    if (!id) return null;\n    var heading = document.getElementById(id);\n    if (!heading) return null;\n    return { id: id, element: heading };\n  }).filter(function(entry) {\n    return entry !== null;\n  });\n\n  if (!headings.length) return;\n\n  var currentActive = '';\n\n  function updateActiveLink(activeId) {\n    if (!activeId || currentActive === activeId) return;\n    currentActive = activeId;\n\n    tocLinks.forEach(function(link) {\n      var id = getHeadingId(link);\n      link.classList.toggle('toc-active', id === activeId);\n    });\n  }\n\n  function updateFromScroll() {\n    var scrollOffset = 140;\n    var activeId = headings[0].id;\n\n    headings.forEach(function(entry) {\n      if (entry.element.getBoundingClientRect().top <= scrollOffset) {\n        activeId = entry.id;\n      }\n    });\n\n    updateActiveLink(activeId);\n  }\n\n  tocLinks.forEach(function(link) {\n    link.addEventListener('click', function() {\n      var id = getHeadingId(link);\n      if (id) updateActiveLink(id);\n    });\n  });\n\n  window.addEventListener('scroll', updateFromScroll, { passive: true });\n  window.addEventListener('resize', updateFromScroll);\n  updateFromScroll();\n}\n\n\n    function bindToasts() {\n    document.querySelectorAll('.arcane-toast').forEach(function(toast) {\n      if (toast.dataset.arcaneInteractive === 'true') return;\n      bindSingleToast(toast);\n    });\n\n    document.querySelectorAll('.arcane-toaster').forEach(function(toaster) {\n      if (toaster.dataset.arcaneInteractive === 'true') return;\n      toaster.dataset.arcaneInteractive = 'true';\n\n      var observer = new MutationObserver(function(mutations) {\n        mutations.forEach(function(mutation) {\n          mutation.addedNodes.forEach(function(node) {\n            if (node.nodeType === 1 && node.classList && node.classList.contains('arcane-toast')) {\n              bindSingleToast(node);\n            }\n          });\n        });\n      });\n      observer.observe(toaster, { childList: true, subtree: true });\n    });\n\n    document.querySelectorAll('button').forEach(function(btn) {\n      var text = (btn.textContent || '').toLowerCase();\n      if (btn.dataset.arcaneToastBound) return;\n\n      var isToastButton = text.includes('success') || text.includes('error') ||\n                          text.includes('warning') || text.includes('info') ||\n                          text.includes('loading') || text.includes('toast') ||\n                          text.includes('acknowledge') || text.includes('confirm action') ||\n                          text.includes('with undo');\n      if (!isToastButton) return;\n\n      btn.dataset.arcaneToastBound = 'true';\n      btn.addEventListener('click', function(e) {\n        if (text.includes('acknowledge')) {\n          createToast('warning', 'Your session will expire in 5 minutes.', {\n            title: 'Session Warning',\n            duration: 0,\n            action: { label: 'OK', onPressed: function() {} }\n          });\n          return;\n        }\n        if (text.includes('confirm action')) {\n          createToast('error', 'This action cannot be undone.', {\n            title: 'Are you sure?',\n            duration: 0,\n            action: { label: 'I Understand', onPressed: function() {}, destructive: true }\n          });\n          return;\n        }\n        if (text.includes('with undo')) {\n          createToast('info', 'Item moved to trash.', {\n            title: 'Deleted',\n            action: { label: 'Undo', onPressed: function() {} }\n          });\n          return;\n        }\n\n        var variant = 'info';\n        if (text.includes('success')) variant = 'success';\n        else if (text.includes('error')) variant = 'error';\n        else if (text.includes('warning')) variant = 'warning';\n        else if (text.includes('loading')) variant = 'loading';\n\n        createToast(variant, getToastMessage(variant));\n      });\n    });\n  }\n\n  function bindSingleToast(toast) {\n    if (toast.dataset.arcaneInteractive === 'true') return;\n    toast.dataset.arcaneInteractive = 'true';\n\n    var duration = toast.dataset.duration !== undefined && toast.dataset.duration !== '' ? parseInt(toast.dataset.duration) : 4000;\n    var dismissible = toast.dataset.dismissible !== 'false';\n    var variant = toast.dataset.variant || 'info';\n    var dismissTimer = null;\n    var isPaused = false;\n\n    toast.style.opacity = '0';\n    toast.style.transform = 'translateY(16px) scale(0.95)';\n    requestAnimationFrame(function() {\n      toast.style.transition = 'all 300ms cubic-bezier(0, 0, 0.2, 1)';\n      toast.style.opacity = '1';\n      toast.style.transform = 'translateY(0) scale(1)';\n    });\n\n    var progressBar = toast.querySelector('.arcane-toast-progress');\n    if (progressBar && duration > 0) {\n      progressBar.style.width = '100%';\n      progressBar.style.transition = 'width ' + duration + 'ms linear';\n      requestAnimationFrame(function() {\n        requestAnimationFrame(function() {\n          progressBar.style.width = '0%';\n        });\n      });\n    }\n\n    function startDismissTimer() {\n      if (duration > 0 && !isPaused) {\n        dismissTimer = setTimeout(function() {\n          dismissToast(toast);\n        }, duration);\n      }\n    }\n    startDismissTimer();\n\n    toast.addEventListener('mouseenter', function() {\n      isPaused = true;\n      if (dismissTimer) {\n        clearTimeout(dismissTimer);\n        dismissTimer = null;\n      }\n      if (progressBar) {\n        var computed = window.getComputedStyle(progressBar);\n        progressBar.style.width = computed.width;\n        progressBar.style.transition = 'none';\n      }\n    });\n\n    toast.addEventListener('mouseleave', function() {\n      isPaused = false;\n      if (duration > 0) {\n        dismissTimer = setTimeout(function() {\n          dismissToast(toast);\n        }, 2000);\n        if (progressBar) {\n          progressBar.style.transition = 'width 2000ms linear';\n          progressBar.style.width = '0%';\n        }\n      }\n    });\n\n    var closeBtn = toast.querySelector('.arcane-toast-close');\n    if (closeBtn) {\n      closeBtn.addEventListener('click', function(e) {\n        e.stopPropagation();\n        if (dismissTimer) clearTimeout(dismissTimer);\n        dismissToast(toast);\n      });\n    }\n\n    var actionBtn = toast.querySelector('.arcane-toast-action');\n    if (actionBtn && !actionBtn.dataset.arcaneActionBound) {\n      actionBtn.dataset.arcaneActionBound = 'true';\n      actionBtn.addEventListener('click', function(e) {\n        e.stopPropagation();\n        if (dismissTimer) clearTimeout(dismissTimer);\n        dismissToast(toast);\n      });\n    }\n  }\n\n  function dismissToast(toast) {\n    if (toast.dataset.dismissed === 'true') return;\n    toast.dataset.dismissed = 'true';\n\n    toast.style.transition = 'all 200ms cubic-bezier(0.4, 0, 1, 1)';\n    toast.style.opacity = '0';\n    toast.style.transform = 'translateY(-16px) scale(0.95)';\n\n    setTimeout(function() {\n      if (toast.parentNode) {\n        toast.parentNode.removeChild(toast);\n      }\n    }, 200);\n  }\n\n  function getToastMessage(variant) {\n    var messages = {\n      success: 'Action completed successfully!',\n      error: 'Something went wrong. Please try again.',\n      warning: 'Please review before continuing.',\n      info: 'Here is some helpful information.',\n      loading: 'Processing your request...'\n    };\n    return messages[variant] || messages.info;\n  }\n\n  function getToastTitle(variant) {\n    return {\n      success: 'Success',\n      error: 'Error',\n      warning: 'Warning',\n      info: 'Information',\n      loading: 'Loading'\n    }[variant] || 'Notification';\n  }\n\n  function createToast(variant, message, options) {\n    options = options || {};\n    var title = options.title || getToastTitle(variant);\n    var description = options.description || '';\n    var position = options.position || 'bottomRight';\n    var duration = options.duration !== undefined ? options.duration : (variant === 'loading' ? 0 : 4000);\n    var action = options.action || null;\n\n    var toaster = document.querySelector('.arcane-toaster[data-position=\"' + position + '\"]') ||\n                  document.querySelector('.arcane-toaster') ||\n                  createToasterContainer(position);\n\n    var colors = {\n      success: { bg: 'rgba(16, 185, 129, 0.05)', border: 'rgba(16, 185, 129, 0.3)', icon: '#10b981', progress: '#10b981' },\n      error: { bg: 'rgba(239, 68, 68, 0.05)', border: 'rgba(239, 68, 68, 0.3)', icon: '#ef4444', progress: '#ef4444' },\n      warning: { bg: 'rgba(245, 158, 11, 0.05)', border: 'rgba(245, 158, 11, 0.3)', icon: '#f59e0b', progress: '#f59e0b' },\n      info: { bg: 'rgba(59, 130, 246, 0.05)', border: 'rgba(59, 130, 246, 0.3)', icon: '#3b82f6', progress: '#3b82f6' },\n      loading: { bg: 'var(--arcane-surface, #1a1a2e)', border: 'var(--arcane-border, #2d2d44)', icon: 'var(--arcane-accent, #10b981)', progress: 'var(--arcane-accent, #10b981)' }\n    };\n    var c = colors[variant] || colors.info;\n\n    var icons = {\n      success: '<svg width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M22 11.08V12a10 10 0 1 1-5.93-9.14\"></path><polyline points=\"22 4 12 14.01 9 11.01\"></polyline></svg>',\n      error: '<svg width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><circle cx=\"12\" cy=\"12\" r=\"10\"></circle><line x1=\"15\" y1=\"9\" x2=\"9\" y2=\"15\"></line><line x1=\"9\" y1=\"9\" x2=\"15\" y2=\"15\"></line></svg>',\n      warning: '<svg width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z\"></path><line x1=\"12\" y1=\"9\" x2=\"12\" y2=\"13\"></line><line x1=\"12\" y1=\"17\" x2=\"12.01\" y2=\"17\"></line></svg>',\n      info: '<svg width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><circle cx=\"12\" cy=\"12\" r=\"10\"></circle><line x1=\"12\" y1=\"16\" x2=\"12\" y2=\"12\"></line><line x1=\"12\" y1=\"8\" x2=\"12.01\" y2=\"8\"></line></svg>',\n      loading: '<svg width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" style=\"animation: arcane-toast-spin 1s linear infinite\"><path d=\"M21 12a9 9 0 1 1-6.219-8.56\"></path></svg>'\n    };\n\n    var toast = document.createElement('div');\n    toast.className = 'arcane-toast arcane-toast-' + variant;\n    toast.setAttribute('role', 'alert');\n    toast.dataset.variant = variant;\n    toast.dataset.duration = duration;\n    var requiresAction = duration === 0 && action && action.label;\n    var isDismissible = variant !== 'loading' && !requiresAction;\n    toast.dataset.dismissible = isDismissible ? 'true' : 'false';\n\n    toast.style.cssText = 'display: flex; align-items: flex-start; gap: 12px; padding: 16px; background: ' + c.bg + '; border: 1px solid ' + c.border + '; border-radius: 12px; box-shadow: 0 10px 15px -3px rgba(0,0,0,0.1); min-width: 320px; max-width: 420px; pointer-events: auto; position: relative; overflow: hidden; opacity: 0; transform: translateY(16px) scale(0.95);';\n\n    var closeButton = isDismissible ? '<button class=\"arcane-toast-close\" type=\"button\" style=\"display: flex; align-items: center; justify-content: center; width: 24px; height: 24px; padding: 0; border: none; background: transparent; color: var(--arcane-muted, #6b7280); cursor: pointer; border-radius: 6px;\"><svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><line x1=\"18\" y1=\"6\" x2=\"6\" y2=\"18\"></line><line x1=\"6\" y1=\"6\" x2=\"18\" y2=\"18\"></line></svg></button>' : '';\n    var progressBar = duration > 0 ? '<div class=\"arcane-toast-progress\" style=\"position: absolute; bottom: 0; left: 0; height: 2px; width: 100%; background: ' + c.progress + ';\"></div>' : '';\n    var actionBtnHtml = '';\n    if (action && action.label) {\n      var actionColor = action.destructive ? 'var(--arcane-error, #ef4444)' : 'var(--arcane-accent, #10b981)';\n      actionBtnHtml = '<div style=\"margin-top: 8px;\"><button class=\"arcane-toast-action\" type=\"button\" style=\"padding: 4px 8px; font-size: 12px; font-weight: 500; color: ' + actionColor + '; background: transparent; border: 1px solid var(--arcane-border); border-radius: 6px; cursor: pointer;\">' + action.label + '</button></div>';\n    }\n\n    toast.innerHTML = '<div style=\"color: ' + c.icon + ';\">' + (icons[variant] || icons.info) + '</div><div style=\"flex: 1;\"><span style=\"font-weight: 600; font-size: 14px; color: var(--arcane-on-surface);\">' + title + '</span><span style=\"font-size: 14px; color: var(--arcane-muted); display: block;\">' + message + '</span>' + actionBtnHtml + '</div>' + closeButton + progressBar;\n\n    toaster.appendChild(toast);\n\n    if (action && action.onPressed) {\n      var actionBtn = toast.querySelector('.arcane-toast-action');\n      if (actionBtn) {\n        actionBtn.addEventListener('click', function(e) {\n          e.stopPropagation();\n          action.onPressed();\n          dismissToast(toast);\n        });\n      }\n    }\n\n    bindSingleToast(toast);\n    return toast;\n  }\n\n  function createToasterContainer(position) {\n    position = position || 'bottomRight';\n    var positionStyles = {\n      topLeft: 'top: 20px; left: 20px; align-items: flex-start; flex-direction: column;',\n      topCenter: 'top: 20px; left: 50%; transform: translateX(-50%); align-items: center; flex-direction: column;',\n      topRight: 'top: 20px; right: 20px; align-items: flex-end; flex-direction: column;',\n      bottomLeft: 'bottom: 20px; left: 20px; align-items: flex-start; flex-direction: column-reverse;',\n      bottomCenter: 'bottom: 20px; left: 50%; transform: translateX(-50%); align-items: center; flex-direction: column-reverse;',\n      bottomRight: 'bottom: 20px; right: 20px; align-items: flex-end; flex-direction: column-reverse;'\n    };\n\n    var toaster = document.createElement('div');\n    toaster.className = 'arcane-toaster';\n    toaster.dataset.position = position;\n    toaster.style.cssText = 'position: fixed; z-index: 9999; display: flex; gap: 12px; pointer-events: none; ' + (positionStyles[position] || positionStyles.bottomRight);\n    document.body.appendChild(toaster);\n    return toaster;\n  }\n\n  if (!document.getElementById('arcane-toast-keyframes')) {\n    var style = document.createElement('style');\n    style.id = 'arcane-toast-keyframes';\n    style.textContent = '@keyframes arcane-toast-spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }';\n    document.head.appendChild(style);\n  }\n\n  // Track if global click listener is already added\n  var _popoverGlobalListenerAdded = false;\n  var _activePopovers = new Set();\n\n  function bindPopovers() {\n    // Only target actual floating containers, not generic position:relative elements\n    document.querySelectorAll('.arcane-floating-container, .neon-floating-container').forEach(function(container) {\n      // Use the trigger class to find the trigger, not firstElementChild\n      var trigger = container.querySelector('.arcane-floating-trigger') || container.firstElementChild;\n      var popup = container.querySelector('.arcane-floating-content, .arcane-floating-tooltip, [style*=\"position: absolute\"][style*=\"z-index\"]');\n      if (!trigger || !popup || container.dataset.arcanePopoverInteractive) return;\n      container.dataset.arcanePopoverInteractive = 'true';\n\n      var isClick = !popup.closest('.arcane-hovercard');\n\n      if (isClick) {\n        trigger.addEventListener('click', function(e) {\n          e.stopPropagation();\n          var isVisible = popup.style.opacity === '1';\n          popup.style.opacity = isVisible ? '0' : '1';\n          popup.style.visibility = isVisible ? 'hidden' : 'visible';\n          popup.style.pointerEvents = isVisible ? 'none' : 'auto';\n          if (!isVisible) {\n            _activePopovers.add(container);\n          } else {\n            _activePopovers.delete(container);\n          }\n        });\n\n        // Add global listener only once\n        if (!_popoverGlobalListenerAdded) {\n          _popoverGlobalListenerAdded = true;\n          document.addEventListener('click', function(e) {\n            _activePopovers.forEach(function(cont) {\n              if (!cont.contains(e.target)) {\n                var pop = cont.querySelector('.arcane-floating-content, .arcane-floating-tooltip, [style*=\"position: absolute\"][style*=\"z-index\"]');\n                if (pop) {\n                  pop.style.opacity = '0';\n                  pop.style.visibility = 'hidden';\n                  pop.style.pointerEvents = 'none';\n                }\n                _activePopovers.delete(cont);\n              }\n            });\n          });\n        }\n      } else {\n        container.addEventListener('mouseenter', function() {\n          popup.style.opacity = '1';\n          popup.style.visibility = 'visible';\n        });\n        container.addEventListener('mouseleave', function() {\n          popup.style.opacity = '0';\n          popup.style.visibility = 'hidden';\n        });\n      }\n    });\n  }\n\n  function bindTooltips() {\n    document.querySelectorAll('.arcane-tooltip-trigger').forEach(function(trigger) {\n      if (trigger.dataset.arcaneTooltipBound) return;\n      trigger.dataset.arcaneTooltipBound = 'true';\n\n      var tooltip = trigger.querySelector('.arcane-tooltip');\n      if (!tooltip) {\n        var content = trigger.dataset.tooltip;\n        var position = trigger.dataset.tooltipPosition || 'top';\n        tooltip = createTooltipElement(content, position);\n        trigger.appendChild(tooltip);\n      }\n\n      trigger.addEventListener('mouseenter', function() {\n        tooltip.style.opacity = '1';\n        tooltip.style.visibility = 'visible';\n        var position = trigger.dataset.tooltipPosition || 'top';\n        if (position === 'top' || position === 'bottom') {\n          tooltip.style.transform = 'translateX(-50%) translateY(0)';\n        } else {\n          tooltip.style.transform = 'translateY(-50%) translateX(0)';\n        }\n      });\n\n      trigger.addEventListener('mouseleave', function() {\n        tooltip.style.opacity = '0';\n        tooltip.style.visibility = 'hidden';\n      });\n    });\n\n    document.querySelectorAll('[title]:not(.arcane-tooltip-trigger):not([data-no-tooltip])').forEach(function(el) {\n      if (el.dataset.arcaneTooltipBound) return;\n      if (el.dataset.noTooltip) return; // Skip elements that opt out\n      var title = el.getAttribute('title');\n      if (!title) return;\n\n      el.dataset.arcaneTooltipBound = 'true';\n      el.removeAttribute('title');\n\n      var wrapper = document.createElement('span');\n      wrapper.className = 'arcane-tooltip-trigger';\n      wrapper.style.cssText = 'position: relative; display: inline-flex;';\n      wrapper.dataset.tooltip = title;\n      wrapper.dataset.tooltipPosition = 'top';\n\n      el.parentNode.insertBefore(wrapper, el);\n      wrapper.appendChild(el);\n\n      var tooltip = createTooltipElement(title, 'top');\n      wrapper.appendChild(tooltip);\n\n      wrapper.addEventListener('mouseenter', function() {\n        tooltip.style.opacity = '1';\n        tooltip.style.visibility = 'visible';\n        tooltip.style.transform = 'translateX(-50%) translateY(0)';\n      });\n\n      wrapper.addEventListener('mouseleave', function() {\n        tooltip.style.opacity = '0';\n        tooltip.style.visibility = 'hidden';\n      });\n    });\n  }\n\n  function createTooltipElement(content, position) {\n    var tooltip = document.createElement('div');\n    tooltip.className = 'arcane-tooltip arcane-tooltip-' + position;\n    tooltip.setAttribute('role', 'tooltip');\n\n    var positionStyles = {\n      top: 'bottom: 100%; left: 50%; transform: translateX(-50%) translateY(-4px); margin-bottom: 8px;',\n      bottom: 'top: 100%; left: 50%; transform: translateX(-50%) translateY(4px); margin-top: 8px;',\n      left: 'right: 100%; top: 50%; transform: translateY(-50%) translateX(-4px); margin-right: 8px;',\n      right: 'left: 100%; top: 50%; transform: translateY(-50%) translateX(4px); margin-left: 8px;'\n    };\n\n    tooltip.style.cssText = 'position: absolute; z-index: 9999; padding: 6px 12px; font-size: 12px; font-weight: 500; line-height: 1.4; color: var(--arcane-on-surface, #f8fafc); background: var(--arcane-surface, #1e1e2e); border-radius: 8px; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1); white-space: nowrap; pointer-events: none; opacity: 0; visibility: hidden; transition: opacity 150ms ease, visibility 150ms ease, transform 150ms ease; ' + (positionStyles[position] || positionStyles.top);\n\n    tooltip.textContent = content;\n\n    var arrow = document.createElement('div');\n    arrow.className = 'arcane-tooltip-arrow';\n\n    var arrowStyles = {\n      top: 'bottom: -4px; left: 50%; margin-left: -4px;',\n      bottom: 'top: -4px; left: 50%; margin-left: -4px;',\n      left: 'right: -4px; top: 50%; margin-top: -4px;',\n      right: 'left: -4px; top: 50%; margin-top: -4px;'\n    };\n\n    arrow.style.cssText = 'position: absolute; width: 8px; height: 8px; background: var(--arcane-surface, #1e1e2e); transform: rotate(45deg); ' + (arrowStyles[position] || arrowStyles.top);\n\n    tooltip.appendChild(arrow);\n    return tooltip;\n  }\n\n  function bindDialogs() {\n    document.querySelectorAll('.arcane-dialog-overlay, [role=\"dialog\"]').forEach(function(overlay) {\n      if (overlay.dataset.arcaneInteractive === 'true') return;\n      overlay.dataset.arcaneInteractive = 'true';\n\n      var dialog = overlay.querySelector('.arcane-dialog') || overlay;\n      var closeBtn = overlay.querySelector('.arcane-dialog-close, [aria-label=\"Close\"]');\n\n      function closeDialog() {\n        overlay.style.opacity = '0';\n        if (dialog !== overlay) {\n          dialog.style.transform = 'scale(0.95)';\n        }\n        setTimeout(function() {\n          overlay.style.display = 'none';\n          document.body.style.overflow = '';\n        }, 150);\n      }\n\n      if (closeBtn) {\n        closeBtn.addEventListener('click', closeDialog);\n      }\n\n      overlay.addEventListener('click', function(e) {\n        if (e.target === overlay) {\n          closeDialog();\n        }\n      });\n\n      document.addEventListener('keydown', function(e) {\n        if (e.key === 'Escape' && overlay.style.display !== 'none') {\n          closeDialog();\n        }\n      });\n    });\n\n    document.querySelectorAll('button').forEach(function(btn) {\n      var text = (btn.textContent || '').toLowerCase();\n      if (text.includes('open dialog') || text.includes('show dialog')) {\n        if (btn.dataset.arcaneDialogBound) return;\n        btn.dataset.arcaneDialogBound = 'true';\n\n        btn.addEventListener('click', function() {\n          var overlay = document.querySelector('.arcane-dialog-overlay');\n          if (overlay) {\n            document.body.style.overflow = 'hidden';\n            overlay.style.display = 'flex';\n            overlay.style.opacity = '1';\n            var dialog = overlay.querySelector('.arcane-dialog');\n            if (dialog) {\n              dialog.style.transform = 'scale(1)';\n            }\n          }\n        });\n      }\n    });\n  }\n\n  function bindDrawers() {\n    document.querySelectorAll('.arcane-drawer-overlay').forEach(function(overlay) {\n      if (overlay.dataset.arcaneInteractive === 'true') return;\n      overlay.dataset.arcaneInteractive = 'true';\n\n      var drawer = overlay.querySelector('.arcane-drawer');\n      var closeBtn = overlay.querySelector('.arcane-drawer-close');\n\n      function closeDrawer() {\n        overlay.style.opacity = '0';\n        if (drawer) {\n          var position = drawer.dataset.position || 'right';\n          if (position === 'left') {\n            drawer.style.transform = 'translateX(-100%)';\n          } else if (position === 'right') {\n            drawer.style.transform = 'translateX(100%)';\n          } else if (position === 'top') {\n            drawer.style.transform = 'translateY(-100%)';\n          } else if (position === 'bottom') {\n            drawer.style.transform = 'translateY(100%)';\n          }\n        }\n        setTimeout(function() {\n          overlay.style.display = 'none';\n          document.body.style.overflow = '';\n        }, 200);\n      }\n\n      if (closeBtn) {\n        closeBtn.addEventListener('click', closeDrawer);\n      }\n\n      overlay.addEventListener('click', function(e) {\n        if (e.target === overlay) {\n          closeDrawer();\n        }\n      });\n    });\n\n    document.querySelectorAll('button').forEach(function(btn) {\n      var text = (btn.textContent || '').toLowerCase();\n      if (text.includes('open drawer') || text.includes('show drawer')) {\n        if (btn.dataset.arcaneDrawerBound) return;\n        btn.dataset.arcaneDrawerBound = 'true';\n\n        btn.addEventListener('click', function() {\n          var overlay = document.querySelector('.arcane-drawer-overlay');\n          if (overlay) {\n            document.body.style.overflow = 'hidden';\n            overlay.style.display = 'flex';\n            requestAnimationFrame(function() {\n              overlay.style.opacity = '1';\n              var drawer = overlay.querySelector('.arcane-drawer');\n              if (drawer) {\n                drawer.style.transform = 'translateX(0)';\n              }\n            });\n          }\n        });\n      }\n    });\n  }\n\n  function bindMobileMenus() {\n    document.querySelectorAll('.arcane-mobile-menu-trigger, [aria-label*=\"Menu\"]').forEach(function(trigger) {\n      if (trigger.dataset.arcaneInteractive === 'true') return;\n      trigger.dataset.arcaneInteractive = 'true';\n\n      trigger.addEventListener('click', function() {\n        var overlay = document.querySelector('.arcane-mobile-menu-overlay');\n        var menu = document.querySelector('.arcane-mobile-menu');\n\n        if (overlay && menu) {\n          var isOpen = overlay.style.display === 'flex';\n\n          if (isOpen) {\n            overlay.style.opacity = '0';\n            menu.style.transform = 'translateX(-100%)';\n            setTimeout(function() {\n              overlay.style.display = 'none';\n              document.body.style.overflow = '';\n            }, 200);\n          } else {\n            document.body.style.overflow = 'hidden';\n            overlay.style.display = 'flex';\n            requestAnimationFrame(function() {\n              overlay.style.opacity = '1';\n              menu.style.transform = 'translateX(0)';\n            });\n          }\n        }\n      });\n    });\n\n    var overlay = document.querySelector('.arcane-mobile-menu-overlay');\n    if (overlay && !overlay.dataset.arcaneInteractive) {\n      overlay.dataset.arcaneInteractive = 'true';\n\n      overlay.addEventListener('click', function(e) {\n        var menu = overlay.querySelector('.arcane-mobile-menu');\n        if (e.target === overlay) {\n          overlay.style.opacity = '0';\n          if (menu) menu.style.transform = 'translateX(-100%)';\n          setTimeout(function() {\n            overlay.style.display = 'none';\n            document.body.style.overflow = '';\n          }, 200);\n        }\n      });\n\n      var closeBtn = overlay.querySelector('.arcane-mobile-menu-close');\n      if (closeBtn) {\n        closeBtn.addEventListener('click', function() {\n          var menu = overlay.querySelector('.arcane-mobile-menu');\n          overlay.style.opacity = '0';\n          if (menu) menu.style.transform = 'translateX(-100%)';\n          setTimeout(function() {\n            overlay.style.display = 'none';\n            document.body.style.overflow = '';\n          }, 200);\n        });\n      }\n    }\n  }\n\n  function bindSheets() {\n    // Close buttons on sheets\n    document.querySelectorAll('.arcane-sheet .arcane-sheet-close').forEach(function(closeBtn) {\n      if (closeBtn.dataset.arcaneInteractive === 'true') return;\n      closeBtn.dataset.arcaneInteractive = 'true';\n\n      closeBtn.addEventListener('click', function() {\n        var sheet = closeBtn.closest('.arcane-sheet-overlay');\n        if (sheet) {\n          sheet.remove();\n        }\n      });\n    });\n\n    // Backdrop dismiss for sheets\n    document.querySelectorAll('.arcane-sheet-overlay').forEach(function(overlay) {\n      if (overlay.dataset.arcaneInteractive === 'true') return;\n      overlay.dataset.arcaneInteractive = 'true';\n\n      overlay.addEventListener('click', function(e) {\n        if (e.target === overlay && overlay.dataset.barrierDismissible !== 'false') {\n          overlay.remove();\n        }\n      });\n    });\n\n    // Action sheet items\n    document.querySelectorAll('.arcane-action-sheet-item').forEach(function(item) {\n      if (item.dataset.arcaneInteractive === 'true') return;\n      if (item.dataset.disabled === 'true') return;\n      item.dataset.arcaneInteractive = 'true';\n\n      item.addEventListener('click', function() {\n        var actionSheet = item.closest('.arcane-sheet-overlay');\n        if (actionSheet) {\n          actionSheet.remove();\n        }\n      });\n    });\n  }\n\n  function openSheet(position, content, options) {\n    options = options || {};\n    var overlay = document.createElement('div');\n    overlay.className = 'arcane-sheet-overlay';\n    overlay.dataset.barrierDismissible = options.barrierDismissible !== false ? 'true' : 'false';\n    overlay.style.cssText = 'position:fixed;inset:0;z-index:1050;display:flex;align-items:' +\n      (position === 'bottom' ? 'flex-end' : position === 'top' ? 'flex-start' : 'stretch') +\n      ';justify-content:' + (position === 'end' ? 'flex-end' : position === 'start' ? 'flex-start' : 'center') +\n      ';background:var(--arcane-scrim);';\n\n    var sheet = document.createElement('div');\n    sheet.className = 'arcane-sheet arcane-sheet-' + position;\n\n    var baseStyles = 'background:var(--arcane-surface);display:flex;flex-direction:column;overflow:hidden;';\n    if (position === 'bottom' || position === 'top') {\n      sheet.style.cssText = baseStyles + 'width:100%;max-height:' + (options.height || 400) + 'px;border-radius:var(--arcane-radius-lg) var(--arcane-radius-lg) 0 0;';\n    } else {\n      sheet.style.cssText = baseStyles + 'height:100%;width:' + (options.width || 400) + 'px;max-width:100%;';\n    }\n\n    sheet.innerHTML = content;\n    overlay.appendChild(sheet);\n    document.body.appendChild(overlay);\n\n    bindSheets();\n    return overlay;\n  }\n\n  window.ArcaneSheet = { open: openSheet };\n\n  function bindEmailDialogs() {\n    document.querySelectorAll('.arcane-email-dialog').forEach(function(dialog) {\n      if (dialog.dataset.arcaneInteractive === 'true') return;\n      dialog.dataset.arcaneInteractive = 'true';\n\n      var input = dialog.querySelector('.arcane-email-dialog-input input');\n      var errorEl = dialog.querySelector('.arcane-email-dialog-error');\n      var submitBtn = dialog.querySelector('.arcane-email-dialog-submit');\n      var blockedDomains = (dialog.dataset.blockedDomains || '').split(',').filter(Boolean);\n      var requireWork = dialog.dataset.requireWork === 'true';\n\n      if (input) {\n        input.addEventListener('input', function() {\n          validateEmail();\n        });\n\n        input.addEventListener('blur', function() {\n          validateEmail();\n        });\n      }\n\n      if (submitBtn) {\n        submitBtn.addEventListener('click', function() {\n          if (validateEmail()) {\n            dialog.dispatchEvent(new CustomEvent('emailsubmit', {\n              detail: { email: input.value }\n            }));\n          }\n        });\n      }\n\n      function validateEmail() {\n        var email = input.value.trim();\n        var error = null;\n\n        if (!email) {\n          error = null; // No error for empty\n        } else if (!/^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$/.test(email)) {\n          error = 'Please enter a valid email address';\n        } else {\n          var domain = email.split('@')[1].toLowerCase();\n\n          // Check blocked domains\n          for (var i = 0; i < blockedDomains.length; i++) {\n            if (domain === blockedDomains[i].toLowerCase()) {\n              error = 'This email domain is not allowed';\n              break;\n            }\n          }\n\n          // Check work email requirement\n          if (!error && requireWork) {\n            var personalDomains = ['gmail.com', 'yahoo.com', 'hotmail.com', 'outlook.com', 'aol.com', 'icloud.com', 'mail.com'];\n            for (var j = 0; j < personalDomains.length; j++) {\n              if (domain === personalDomains[j]) {\n                error = 'Please use a work email address';\n                break;\n              }\n            }\n          }\n        }\n\n        if (errorEl) {\n          errorEl.textContent = error || '';\n          errorEl.style.display = error ? 'block' : 'none';\n        }\n\n        if (submitBtn) {\n          submitBtn.disabled = !!error || !email;\n        }\n\n        input.style.borderColor = error ? 'var(--arcane-destructive)' : 'var(--arcane-border)';\n\n        return !error && email;\n      }\n    });\n  }\n\n  function bindTimeDialogs() {\n    document.querySelectorAll('.arcane-time-dialog').forEach(function(dialog) {\n      if (dialog.dataset.arcaneInteractive === 'true') return;\n      dialog.dataset.arcaneInteractive = 'true';\n\n      var hourCol = dialog.querySelector('.arcane-time-dialog-hour');\n      var minuteCol = dialog.querySelector('.arcane-time-dialog-minute');\n      var periodCol = dialog.querySelector('.arcane-time-dialog-period');\n\n      var selectedHour = parseInt(dialog.dataset.hour || '12');\n      var selectedMinute = parseInt(dialog.dataset.minute || '0');\n      var selectedPeriod = dialog.dataset.period || 'AM';\n\n      // Bind column selections\n      bindTimeColumn(hourCol, 'hour', function(val) {\n        selectedHour = parseInt(val);\n        dialog.dataset.hour = val;\n        updateDisplay();\n      });\n\n      bindTimeColumn(minuteCol, 'minute', function(val) {\n        selectedMinute = parseInt(val);\n        dialog.dataset.minute = val;\n        updateDisplay();\n      });\n\n      bindTimeColumn(periodCol, 'period', function(val) {\n        selectedPeriod = val;\n        dialog.dataset.period = val;\n        updateDisplay();\n      });\n\n      function updateDisplay() {\n        var displayEl = dialog.querySelector('.arcane-time-dialog-display');\n        if (displayEl) {\n          var minStr = selectedMinute.toString().padStart(2, '0');\n          displayEl.textContent = selectedHour + ':' + minStr + ' ' + selectedPeriod;\n        }\n\n        dialog.dispatchEvent(new CustomEvent('timechange', {\n          detail: {\n            hour: selectedHour,\n            minute: selectedMinute,\n            period: selectedPeriod\n          }\n        }));\n      }\n\n      function bindTimeColumn(col, type, onChange) {\n        if (!col) return;\n        var items = col.querySelectorAll('.arcane-time-dialog-item');\n        items.forEach(function(item) {\n          item.addEventListener('click', function() {\n            items.forEach(function(i) {\n              i.classList.remove('selected');\n              i.style.background = 'transparent';\n              i.style.color = 'var(--arcane-on-surface)';\n            });\n            item.classList.add('selected');\n            item.style.background = 'var(--arcane-accent)';\n            item.style.color = 'var(--arcane-accent-foreground)';\n            onChange(item.dataset.value);\n          });\n        });\n      }\n    });\n  }\n\n  function bindItemPickers() {\n    document.querySelectorAll('.arcane-item-picker-content').forEach(function(picker) {\n      if (picker.dataset.arcaneInteractive === 'true') return;\n      picker.dataset.arcaneInteractive = 'true';\n\n      var searchInput = picker.querySelector('.arcane-item-picker-search input');\n      var items = picker.querySelectorAll('.arcane-item-picker-item');\n      var isMultiSelect = picker.dataset.multiSelect === 'true';\n      var selectedItems = new Set();\n\n      // Search filtering\n      if (searchInput) {\n        searchInput.addEventListener('input', function() {\n          var query = searchInput.value.toLowerCase();\n          items.forEach(function(item) {\n            var text = item.textContent.toLowerCase();\n            item.style.display = text.includes(query) ? 'flex' : 'none';\n          });\n        });\n      }\n\n      // Item selection\n      items.forEach(function(item) {\n        item.addEventListener('click', function() {\n          var itemId = item.dataset.itemId || item.textContent;\n\n          if (isMultiSelect) {\n            if (selectedItems.has(itemId)) {\n              selectedItems.delete(itemId);\n              item.classList.remove('selected');\n              item.style.background = 'transparent';\n              item.style.borderColor = 'var(--arcane-border)';\n            } else {\n              selectedItems.add(itemId);\n              item.classList.add('selected');\n              item.style.background = 'var(--arcane-accent-container)';\n              item.style.borderColor = 'var(--arcane-accent)';\n            }\n\n            // Update selection count\n            var counter = picker.querySelector('.arcane-item-picker-count');\n            if (counter) {\n              counter.textContent = selectedItems.size + ' item(s) selected';\n              counter.style.display = selectedItems.size > 0 ? 'block' : 'none';\n            }\n          } else {\n            // Single select - deselect others\n            items.forEach(function(i) {\n              i.classList.remove('selected');\n              i.style.background = 'transparent';\n              i.style.borderColor = 'var(--arcane-border)';\n            });\n            item.classList.add('selected');\n            item.style.background = 'var(--arcane-accent-container)';\n            item.style.borderColor = 'var(--arcane-accent)';\n          }\n\n          picker.dispatchEvent(new CustomEvent('itemselect', {\n            detail: {\n              item: itemId,\n              selected: isMultiSelect ? Array.from(selectedItems) : itemId\n            }\n          }));\n        });\n      });\n    });\n  }\n\n  function bindChatScreens() {\n    document.querySelectorAll('.arcane-chat-screen').forEach(function(chat) {\n      if (chat.dataset.arcaneInteractive === 'true') return;\n      chat.dataset.arcaneInteractive = 'true';\n\n      var inputArea = chat.querySelector('.arcane-chat-input');\n      var sendBtn = chat.querySelector('.arcane-chat-send');\n      var messagesArea = chat.querySelector('.arcane-chat-messages');\n\n      if (inputArea && sendBtn) {\n        // Auto-resize textarea\n        inputArea.addEventListener('input', function() {\n          inputArea.style.height = 'auto';\n          inputArea.style.height = Math.min(inputArea.scrollHeight, 120) + 'px';\n        });\n\n        // Send on enter (without shift)\n        inputArea.addEventListener('keydown', function(e) {\n          if (e.key === 'Enter' && !e.shiftKey) {\n            e.preventDefault();\n            sendMessage();\n          }\n        });\n\n        // Send button click\n        sendBtn.addEventListener('click', function() {\n          sendMessage();\n        });\n      }\n\n      function sendMessage() {\n        var text = inputArea.value.trim();\n        if (!text) return;\n\n        // Dispatch event for handling\n        chat.dispatchEvent(new CustomEvent('sendmessage', {\n          detail: { text: text }\n        }));\n\n        // Clear input\n        inputArea.value = '';\n        inputArea.style.height = 'auto';\n\n        // Scroll to bottom\n        if (messagesArea) {\n          messagesArea.scrollTop = messagesArea.scrollHeight;\n        }\n      }\n\n      // Auto-scroll when new messages arrive\n      if (messagesArea) {\n        var observer = new MutationObserver(function(mutations) {\n          mutations.forEach(function(mutation) {\n            if (mutation.addedNodes.length) {\n              messagesArea.scrollTop = messagesArea.scrollHeight;\n            }\n          });\n        });\n\n        observer.observe(messagesArea, { childList: true });\n      }\n    });\n  }\n\n\n      // ===== MAP SHIFT+HOVER COORDINATE MODE =====\n  var _shiftKeyHeld = false;\n  var _mapCoordTooltips = {};\n  var _mapCoordCounter = 0;\n\n  function bindMapCoordinateMode() {\n    // Track Shift key globally (only bind once)\n    if (!window._arcaneShiftKeyBound) {\n      window._arcaneShiftKeyBound = true;\n\n      document.addEventListener('keydown', function(e) {\n        if (e.key === 'Shift' && !_shiftKeyHeld) {\n          _shiftKeyHeld = true;\n          document.querySelectorAll('.arcane-world-map, .arcane-usa-map').forEach(function(map) {\n            map.style.cursor = 'crosshair';\n          });\n        }\n      });\n\n      document.addEventListener('keyup', function(e) {\n        if (e.key === 'Shift') {\n          _shiftKeyHeld = false;\n          document.querySelectorAll('.arcane-world-map, .arcane-usa-map').forEach(function(map) {\n            map.style.cursor = '';\n          });\n          // Hide all coord tooltips\n          Object.values(_mapCoordTooltips).forEach(function(tooltip) {\n            if (tooltip) {\n              tooltip.style.opacity = '0';\n              tooltip.style.visibility = 'hidden';\n            }\n          });\n        }\n      });\n\n      // Watch for new maps being added (for tabs, etc.)\n      var observer = new MutationObserver(function(mutations) {\n        mutations.forEach(function(mutation) {\n          mutation.addedNodes.forEach(function(node) {\n            if (node.nodeType === 1) {\n              // Check if the added node is a map or contains maps\n              if (node.classList && (node.classList.contains('arcane-world-map') || node.classList.contains('arcane-usa-map'))) {\n                bindSingleMap(node);\n              }\n              // Also check children\n              if (node.querySelectorAll) {\n                node.querySelectorAll('.arcane-world-map, .arcane-usa-map').forEach(function(map) {\n                  bindSingleMap(map);\n                });\n              }\n            }\n          });\n        });\n      });\n      observer.observe(document.body, { childList: true, subtree: true });\n    }\n\n    // Bind to all existing maps\n    bindAllMaps();\n  }\n\n  function bindAllMaps() {\n    document.querySelectorAll('.arcane-world-map, .arcane-usa-map').forEach(function(map) {\n      bindSingleMap(map);\n    });\n  }\n\n  function bindSingleMap(map) {\n    if (map.dataset.arcaneCoordModeBound) return;\n    map.dataset.arcaneCoordModeBound = 'true';\n    bindMapCoordinates(map);\n  }\n\n  function bindMapCoordinates(map) {\n    // Create tooltip and append to body (not map) to avoid Jaspr DOM conflicts\n    var tooltip = createMapCoordTooltip();\n    var mapId = 'coord-map-' + (_mapCoordCounter++);\n    map.dataset.coordMapId = mapId;\n    _mapCoordTooltips[mapId] = tooltip;\n    document.body.appendChild(tooltip);\n\n    // Helper to get map dimensions based on class at event time\n    function getMapDimensions(mapEl) {\n      var isUsa = mapEl.classList.contains('arcane-usa-map');\n      return {\n        width: isUsa ? 1000 : 2000,\n        height: isUsa ? 589 : 857,\n        latMax: isUsa ? 50 : 83,\n        latRange: isUsa ? 25 : 143,\n        isUsa: isUsa\n      };\n    }\n\n    function calculateCoords(relX, relY, dims) {\n      var svgX = relX * dims.width;\n      var svgY = relY * dims.height;\n\n      var lat, lng;\n      if (dims.isUsa) {\n        // USA map projection inverse\n        lng = ((svgX - 50) / 900) * 58 - 125;\n        lat = 50 - ((svgY - 50) / 450) * 25;\n      } else {\n        // World map projection (equirectangular)\n        lng = (svgX / dims.width) * 360 - 180;\n        lat = dims.latMax - (svgY / dims.height) * dims.latRange;\n      }\n\n      return { lat: lat, lng: lng, svgX: svgX, svgY: svgY };\n    }\n\n    map.addEventListener('mousemove', function(e) {\n      if (!_shiftKeyHeld) {\n        tooltip.style.opacity = '0';\n        tooltip.style.visibility = 'hidden';\n        return;\n      }\n\n      var dims = getMapDimensions(map);\n      var rect = map.getBoundingClientRect();\n      var mouseX = e.clientX - rect.left;\n      var mouseY = e.clientY - rect.top;\n      var relX = Math.max(0, Math.min(1, mouseX / rect.width));\n      var relY = Math.max(0, Math.min(1, mouseY / rect.height));\n\n      var coords = calculateCoords(relX, relY, dims);\n\n      // Position tooltip fixed to viewport\n      tooltip.style.position = 'fixed';\n      tooltip.style.left = (e.clientX) + 'px';\n      tooltip.style.top = (e.clientY + 20) + 'px';\n      tooltip.style.transform = 'translateX(-50%)';\n      tooltip.style.opacity = '1';\n      tooltip.style.visibility = 'visible';\n\n      // Update content\n      var latEl = tooltip.querySelector('.coord-lat');\n      var lngEl = tooltip.querySelector('.coord-lng');\n      var svgEl = tooltip.querySelector('.coord-svg');\n\n      if (latEl) latEl.textContent = 'Lat: ' + coords.lat.toFixed(4);\n      if (lngEl) lngEl.textContent = 'Lng: ' + coords.lng.toFixed(4);\n      if (svgEl) svgEl.textContent = 'SVG: ' + Math.round(coords.svgX) + ', ' + Math.round(coords.svgY);\n    });\n\n    map.addEventListener('mouseleave', function() {\n      tooltip.style.opacity = '0';\n      tooltip.style.visibility = 'hidden';\n    });\n\n    map.addEventListener('click', function(e) {\n      // Always stop propagation and prevent default to prevent Jaspr DOM reconciliation issues\n      e.stopPropagation();\n      e.preventDefault();\n\n      if (!_shiftKeyHeld) return;\n\n      var dims = getMapDimensions(map);\n      var rect = map.getBoundingClientRect();\n      var relX = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));\n      var relY = Math.max(0, Math.min(1, (e.clientY - rect.top) / rect.height));\n\n      var coords = calculateCoords(relX, relY, dims);\n\n      var coordText = 'Lat: ' + coords.lat.toFixed(4) + ', Lng: ' + coords.lng.toFixed(4) + ', SVG: (' + Math.round(coords.svgX) + ', ' + Math.round(coords.svgY) + ')';\n\n      if (navigator.clipboard && navigator.clipboard.writeText) {\n        navigator.clipboard.writeText(coordText).then(function() {\n          showCoordCopiedFeedback(tooltip);\n        }).catch(function(err) {\n          console.warn('[Arcane] Clipboard write failed:', err);\n          fallbackCopyToClipboard(coordText, tooltip);\n        });\n      } else {\n        fallbackCopyToClipboard(coordText, tooltip);\n      }\n    }, true);\n  }\n\n  function fallbackCopyToClipboard(text, tooltip) {\n    var textArea = document.createElement('textarea');\n    textArea.value = text;\n    textArea.style.position = 'fixed';\n    textArea.style.left = '-9999px';\n    document.body.appendChild(textArea);\n    textArea.select();\n    try {\n      document.execCommand('copy');\n      showCoordCopiedFeedback(tooltip);\n    } catch (err) {\n      console.warn('[Arcane] Fallback copy failed:', err);\n    }\n    document.body.removeChild(textArea);\n  }\n\n  function createMapCoordTooltip() {\n    var tooltip = document.createElement('div');\n    tooltip.className = 'arcane-map-coord-tooltip';\n    tooltip.style.cssText = 'position: fixed; z-index: 9999; pointer-events: none; opacity: 0; visibility: hidden; transition: opacity 150ms ease;';\n\n    var inner = document.createElement('div');\n    inner.style.cssText = 'background: rgba(30, 30, 46, 0.95); border: 1px solid #4b5563; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.5); padding: 10px 14px; backdrop-filter: blur(8px);';\n\n    var latDiv = document.createElement('div');\n    latDiv.className = 'coord-lat';\n    latDiv.style.cssText = 'font-size: 13px; font-family: ui-monospace, monospace; color: #f8fafc; white-space: nowrap; font-weight: 500;';\n    latDiv.textContent = 'Lat: 0.0000';\n\n    var lngDiv = document.createElement('div');\n    lngDiv.className = 'coord-lng';\n    lngDiv.style.cssText = 'font-size: 13px; font-family: ui-monospace, monospace; color: #f8fafc; white-space: nowrap; font-weight: 500;';\n    lngDiv.textContent = 'Lng: 0.0000';\n\n    var svgDiv = document.createElement('div');\n    svgDiv.className = 'coord-svg';\n    svgDiv.style.cssText = 'font-size: 11px; font-family: ui-monospace, monospace; color: #9ca3af; white-space: nowrap; margin-top: 6px;';\n    svgDiv.textContent = 'SVG: 0, 0';\n\n    var hintDiv = document.createElement('div');\n    hintDiv.className = 'coord-hint';\n    hintDiv.style.cssText = 'font-size: 11px; color: #60a5fa; margin-top: 6px; font-weight: 500;';\n    hintDiv.textContent = 'Click to copy';\n\n    inner.appendChild(latDiv);\n    inner.appendChild(lngDiv);\n    inner.appendChild(svgDiv);\n    inner.appendChild(hintDiv);\n    tooltip.appendChild(inner);\n\n    return tooltip;\n  }\n\n  function showCoordCopiedFeedback(tooltip) {\n    var hint = tooltip.querySelector('.coord-hint');\n    if (hint) {\n      var original = hint.textContent;\n      hint.textContent = 'Copied!';\n      hint.style.color = '#22c55e';\n      setTimeout(function() {\n        hint.textContent = original;\n        hint.style.color = '#60a5fa';\n      }, 1500);\n    }\n  }\n\n  // Legacy function name for backward compatibility\n  function bindMapDebugMode() {\n    bindMapCoordinateMode();\n  }\n\n  // ===== MAP PIN TOOLTIPS =====\n  function bindMapPinTooltips() {\n    // Set up observer for dynamically added maps (only once)\n    if (!window._arcanePinTooltipsObserver) {\n      window._arcanePinTooltipsObserver = true;\n      var observer = new MutationObserver(function(mutations) {\n        mutations.forEach(function(mutation) {\n          mutation.addedNodes.forEach(function(node) {\n            if (node.nodeType === 1 && node.querySelectorAll) {\n              node.querySelectorAll('.arcane-world-map[data-has-tooltips=\"true\"], .arcane-usa-map[data-has-tooltips=\"true\"]').forEach(function(map) {\n                if (!map.dataset.arcaneMapTooltipsBound) {\n                  map.dataset.arcaneMapTooltipsBound = 'true';\n                  bindPinTooltips(map);\n                }\n              });\n              // Also bind location list items\n              node.querySelectorAll('.location-list-item').forEach(function(item) {\n                bindLocationListItem(item);\n              });\n            }\n          });\n        });\n      });\n      observer.observe(document.body, { childList: true, subtree: true });\n    }\n\n    document.querySelectorAll('.arcane-world-map[data-has-tooltips=\"true\"], .arcane-usa-map[data-has-tooltips=\"true\"]').forEach(function(map) {\n      if (map.dataset.arcaneMapTooltipsBound) return;\n      map.dataset.arcaneMapTooltipsBound = 'true';\n      bindPinTooltips(map);\n    });\n  }\n\n  function bindPinTooltips(map) {\n    var pins = map.querySelectorAll('.arcane-map-pin[data-location]');\n    pins.forEach(function(pin) {\n      var locationId = pin.getAttribute('data-location');\n      if (!locationId) return;\n\n      pin.addEventListener('mouseenter', function() {\n        if (_shiftKeyHeld) return;\n        highlightMapPin(map, locationId);\n      });\n\n      pin.addEventListener('mouseleave', function() {\n        unhighlightMapPin(map, locationId);\n      });\n    });\n  }\n\n  function highlightMapPin(map, locationId) {\n    var pin = map.querySelector('.arcane-map-pin[data-location=\"' + locationId + '\"]');\n    var tooltip = map.querySelector('.arcane-map-tooltip[data-for-location=\"' + locationId + '\"]');\n\n    if (pin) {\n      pin.style.transform = 'translate(-50%, -50%) scale(1.5)';\n      pin.style.boxShadow = '0 0 20px 10px rgba(34, 197, 94, 0.6)';\n    }\n    if (tooltip) {\n      tooltip.style.opacity = '1';\n      tooltip.style.visibility = 'visible';\n    }\n  }\n\n  function unhighlightMapPin(map, locationId) {\n    var pin = map.querySelector('.arcane-map-pin[data-location=\"' + locationId + '\"]');\n    var tooltip = map.querySelector('.arcane-map-tooltip[data-for-location=\"' + locationId + '\"]');\n\n    if (pin) {\n      pin.style.transform = 'translate(-50%, -50%) scale(1)';\n      pin.style.boxShadow = '';\n    }\n    if (tooltip) {\n      tooltip.style.opacity = '0';\n      tooltip.style.visibility = 'hidden';\n    }\n  }\n\n  // ===== LOCATION LIST HOVER =====\n  function bindLocationListHover() {\n    document.querySelectorAll('.location-list-item').forEach(function(item) {\n      bindLocationListItem(item);\n    });\n  }\n\n  function bindLocationListItem(item) {\n    if (item.dataset.arcaneListHoverBound) return;\n    item.dataset.arcaneListHoverBound = 'true';\n\n    var itemId = item.getAttribute('id');\n    var locationId = itemId ? itemId.replace('location-item-', '') : null;\n    if (!locationId) return;\n\n    item.addEventListener('mouseenter', function() {\n      if (_shiftKeyHeld) return;\n      // Find the closest visible map\n      var mapContainer = document.querySelector('.arcane-world-map, .arcane-usa-map');\n      if (!mapContainer) return;\n\n      item.style.background = 'var(--arcane-surface-variant, rgba(255,255,255,0.05))';\n      highlightMapPin(mapContainer, locationId);\n    });\n\n    item.addEventListener('mouseleave', function() {\n      var mapContainer = document.querySelector('.arcane-world-map, .arcane-usa-map');\n      item.style.background = '';\n      if (mapContainer) {\n        unhighlightMapPin(mapContainer, locationId);\n      }\n    });\n  }\n\n\n    // Rainbow theme animation - smooth hue rotation\n  // ONLY activates if .neon-rainbow class is already present\n  function bindRainbowTheme() {\n    let root = document.querySelector('.neon-rainbow');\n\n    // Only proceed if .neon-rainbow class is explicitly set\n    if (!root) {\n      return; // Do nothing if rainbow theme is not enabled\n    }\n\n    if (root.dataset.rainbowBound) return;\n    root.dataset.rainbowBound = 'true';\n\n    const docRoot = document.documentElement;\n    const duration = 15000; // 15 seconds for very smooth cycle\n    let startTime = null;\n\n    // HSL to RGB values (0-255)\n    function hslToRgb(h, s, l) {\n      s /= 100; l /= 100;\n      const a = s * Math.min(l, 1 - l);\n      const f = n => {\n        const k = (n + h / 30) % 12;\n        return Math.round(255 * (l - a * Math.max(Math.min(k - 3, 9 - k, 1), -1)));\n      };\n      return [f(0), f(8), f(4)];\n    }\n\n    function rgbToHex(r, g, b) {\n      return '#' + [r, g, b].map(x => x.toString(16).padStart(2, '0')).join('');\n    }\n\n    function animate(timestamp) {\n      if (!startTime) startTime = timestamp;\n      const progress = ((timestamp - startTime) % duration) / duration;\n      const hue = progress * 360; // Smooth float, not integer\n\n      const isDark = root.classList.contains('dark');\n      const s = isDark ? 85 : 75;\n      const l = isDark ? 55 : 50;\n\n      const [r, g, b] = hslToRgb(hue, s, l);\n      const primary = rgbToHex(r, g, b);\n      const [rr, rg, rb] = hslToRgb(hue, s, l - 10);\n      const ring = rgbToHex(rr, rg, rb);\n\n      // Set all color variables at once\n      docRoot.style.setProperty('--primary', primary);\n      docRoot.style.setProperty('--ring', ring);\n      docRoot.style.setProperty('--primary-rgb', r + ',' + g + ',' + b);\n      docRoot.style.setProperty('--glow-color', 'rgba(' + r + ',' + g + ',' + b + ',0.35)');\n\n      requestAnimationFrame(animate);\n    }\n\n    requestAnimationFrame(animate);\n  }\n\n    function bindCarousels() {\n    document.querySelectorAll('.arcane-carousel').forEach(function(carousel) {\n      if (carousel.dataset.arcaneCarouselBound) return;\n      carousel.dataset.arcaneCarouselBound = 'true';\n\n      var track = carousel.querySelector('.arcane-carousel-track');\n      if (!track) return;\n\n      var isDragging = false;\n      var startX = 0;\n      var currentTranslateX = 0;\n      var dragStartTranslateX = 0;\n      var hasInteracted = false;\n      var resumeTimer = null;\n      var trackWidth = 0;\n      var animationDuration = parseInt(carousel.dataset.animationDuration) || 60;\n      var resumeDelay = parseInt(carousel.dataset.resumeDelay) || 5000;\n\n      // Velocity tracking for momentum\n      var lastX = 0;\n      var lastTime = 0;\n      var velocity = 0;\n      var momentumAnimationId = null;\n\n      function getTrackWidth() {\n        return track.scrollWidth / 2;\n      }\n\n      function parseTranslateX(transform) {\n        if (transform === 'none' || !transform) return 0;\n        var match = transform.match(/matrix\\(([^)]+)\\)/);\n        if (match) {\n          var values = match[1].split(',');\n          if (values.length >= 5) {\n            return parseFloat(values[4].trim()) || 0;\n          }\n        }\n        return 0;\n      }\n\n      function applyTransform() {\n        track.style.transform = 'translateX(' + currentTranslateX + 'px)';\n      }\n\n      function wrapPosition() {\n        if (trackWidth <= 0) return;\n        while (currentTranslateX > 0) {\n          currentTranslateX -= trackWidth;\n          dragStartTranslateX -= trackWidth;\n        }\n        while (currentTranslateX < -trackWidth) {\n          currentTranslateX += trackWidth;\n          dragStartTranslateX += trackWidth;\n        }\n      }\n\n      function stopMomentum() {\n        if (momentumAnimationId) {\n          cancelAnimationFrame(momentumAnimationId);\n          momentumAnimationId = null;\n        }\n      }\n\n      function startDrag(clientX) {\n        if (resumeTimer) {\n          clearTimeout(resumeTimer);\n          resumeTimer = null;\n        }\n\n        stopMomentum();\n\n        trackWidth = getTrackWidth();\n\n        if (!hasInteracted) {\n          var computedStyle = window.getComputedStyle(track);\n          currentTranslateX = parseTranslateX(computedStyle.transform);\n          hasInteracted = true;\n        }\n\n        isDragging = true;\n        startX = clientX;\n        lastX = clientX;\n        lastTime = performance.now();\n        velocity = 0;\n        dragStartTranslateX = currentTranslateX;\n\n        track.classList.add('dragging');\n        track.classList.remove('resuming');\n        applyTransform();\n      }\n\n      function updateDrag(clientX) {\n        if (!isDragging) return;\n\n        var now = performance.now();\n        var deltaTime = now - lastTime;\n\n        if (deltaTime > 0) {\n          // Calculate velocity (pixels per millisecond)\n          velocity = (clientX - lastX) / deltaTime;\n        }\n\n        lastX = clientX;\n        lastTime = now;\n\n        var deltaX = clientX - startX;\n        currentTranslateX = dragStartTranslateX + deltaX;\n        wrapPosition();\n        applyTransform();\n      }\n\n      function animateMomentum() {\n        // Apply friction to slow down\n        var friction = 0.95;\n        velocity *= friction;\n\n        // Stop when velocity is negligible\n        if (Math.abs(velocity) < 0.01) {\n          stopMomentum();\n          scheduleResume();\n          return;\n        }\n\n        // Move based on velocity (multiply by ~16ms for smooth 60fps)\n        currentTranslateX += velocity * 16;\n        wrapPosition();\n        applyTransform();\n\n        momentumAnimationId = requestAnimationFrame(animateMomentum);\n      }\n\n      function scheduleResume() {\n        if (resumeTimer) clearTimeout(resumeTimer);\n        resumeTimer = setTimeout(resumeAnimation, resumeDelay);\n      }\n\n      function endDrag() {\n        if (!isDragging) return;\n        isDragging = false;\n\n        // If there's significant velocity, start momentum animation\n        if (Math.abs(velocity) > 0.1) {\n          momentumAnimationId = requestAnimationFrame(animateMomentum);\n        } else {\n          scheduleResume();\n        }\n      }\n\n      // Track the current style element for cleanup\n      var currentStyleEl = null;\n\n      function resumeAnimation() {\n        if (!track) return;\n\n        trackWidth = getTrackWidth();\n        if (trackWidth <= 0) return;\n\n        // Normalize position to be within valid range\n        wrapPosition();\n\n        // Clean up previous dynamic style if exists\n        if (currentStyleEl && currentStyleEl.parentNode) {\n          currentStyleEl.parentNode.removeChild(currentStyleEl);\n        }\n\n        // Create a unique animation name for this carousel instance\n        var animName = 'scroll-carousel-resume-' + Date.now();\n\n        // Inject a keyframe animation that starts from the current position\n        currentStyleEl = document.createElement('style');\n        currentStyleEl.textContent = '@keyframes ' + animName + ' { from { transform: translateX(' + currentTranslateX + 'px); } to { transform: translateX(' + (currentTranslateX - trackWidth) + 'px); } }';\n        document.head.appendChild(currentStyleEl);\n\n        // Apply the animation in a single frame to avoid snap\n        // First set the animation, which will immediately start from currentTranslateX\n        track.style.animation = 'none';\n        track.offsetHeight; // Force reflow\n        track.style.transform = '';\n        track.style.animation = animName + ' ' + animationDuration + 's linear infinite';\n\n        track.classList.remove('dragging');\n        track.classList.remove('resuming');\n        hasInteracted = false;\n      }\n\n      // Mouse events\n      track.addEventListener('mousedown', function(e) {\n        e.preventDefault();\n        startDrag(e.clientX);\n      });\n\n      track.addEventListener('mousemove', function(e) {\n        if (isDragging) {\n          e.preventDefault();\n          updateDrag(e.clientX);\n        }\n      });\n\n      document.addEventListener('mouseup', function() {\n        endDrag();\n      });\n\n      // Touch events\n      track.addEventListener('touchstart', function(e) {\n        if (e.touches.length > 0) {\n          startDrag(e.touches[0].clientX);\n        }\n      }, { passive: true });\n\n      track.addEventListener('touchmove', function(e) {\n        if (isDragging && e.touches.length > 0) {\n          updateDrag(e.touches[0].clientX);\n        }\n      }, { passive: true });\n\n      document.addEventListener('touchend', function() {\n        endDrag();\n      });\n\n      document.addEventListener('touchcancel', function() {\n        endDrag();\n      });\n    });\n  }\n\n  // Initial binding\n  if (document.readyState === 'loading') {\n    document.addEventListener('DOMContentLoaded', bindCarousels);\n  } else {\n    bindCarousels();\n  }\n\n  // Re-bind on dynamic content changes\n  var carouselObserver = new MutationObserver(function() {\n    bindCarousels();\n  });\n  carouselObserver.observe(document.body, { childList: true, subtree: true });\n\n})();\n",null)}}
A.ls.prototype={
E(){return"TextColor."+this.b},
gaT(){var s="var(--card-foreground)",r="var(--muted-foreground)",q="var(--warning)",p="var(--secondary-foreground)",o="var(--qn-primary, #059669)",n="var(--foreground)"
switch(this.a){case 0:break
case 1:n=s
break
case 2:n=r
break
case 3:n=r
break
case 4:n="color-mix(in srgb, var(--muted-foreground) 85%, transparent)"
break
case 5:n="color-mix(in srgb, var(--muted-foreground) 65%, transparent)"
break
case 6:n="var(--primary)"
break
case 7:n="var(--accent-foreground)"
break
case 8:n="var(--success)"
break
case 9:n="var(--success-foreground)"
break
case 10:n=q
break
case 11:n="var(--warning-foreground)"
break
case 12:n="var(--destructive)"
break
case 13:n="var(--destructive-foreground)"
break
case 14:n="var(--info)"
break
case 15:n="var(--info-foreground)"
break
case 16:n="var(--primary-foreground)"
break
case 17:n=p
break
case 18:break
case 19:n=s
break
case 20:n=p
break
case 21:n=s
break
case 22:break
case 23:n="var(--popover-foreground)"
break
case 24:n="#FFFFFF"
break
case 25:n="#000000"
break
case 26:n="inherit"
break
case 27:n=o
break
case 28:n=o
break
case 29:n="var(--qn-secondary, #047857)"
break
case 30:n=q
break
default:n=null}return n}}
A.he.prototype={
E(){return"FontSize."+this.b},
gaT(){switch(this.a){case 0:var s="0.625rem"
break
case 1:s="0.75rem"
break
case 2:s="0.875rem"
break
case 3:s="0.9375rem"
break
case 4:s="1rem"
break
case 5:s="1.125rem"
break
case 6:s="1.25rem"
break
case 7:s="1.5rem"
break
case 8:s="2rem"
break
case 9:s="2.5rem"
break
case 10:s="3rem"
break
case 11:s="3.5rem"
break
case 12:s="4.5rem"
break
case 13:s="inherit"
break
default:s=null}return s}}
A.qb.prototype={
E(){return"LineHeight."+this.b},
gaT(){switch(this.a){case 0:var s="1"
break
case 1:s="1.1"
break
case 2:s="1.25"
break
case 3:s="1.5"
break
case 4:s="1.625"
break
case 5:s="2"
break
default:s=null}return s}}
A.kV.prototype={
l(a){var s,r,q,p,o,n,m,l,k=null,j=t.N,i=A.t(j,j)
i.i(0,"display","flex")
i.i(0,"flex-direction","column")
i.i(0,"gap","0.75rem")
i.i(0,"width","100%")
i=A.B(i)
s=t.i
r=A.a([],s)
for(q=this.c,p=q.a,q=q.b,o=0;o<p.length;++o){n=p[o]
m=A.t(j,j)
if(q.v(0,o))m.i(0,"open","")
l=A.a([new A.c(k,k,B.kS,k,k,A.a([new A.k(n.a,k)],s),k),B.mb],s)
r.push(new A.X("details",k,k,k,m,k,A.a([new A.X("summary",k,k,B.ky,k,k,l,k),new A.c(k,k,B.kH,k,k,A.a([new A.c(k,k,B.lo,k,k,A.a([n.c],s),k)],s),k)],s),k))}return new A.c(k,"arcane-accordion faq-container",i,k,k,r,k)}}
A.f9.prototype={
fi(a){var s,r="background-color",q="var(--foreground)"
switch(a.a){case 0:s=t.N
s=A.j([r,"var(--primary)","color","var(--primary-foreground)","border","none"],s,s)
break
case 4:s=t.N
s=A.j([r,"var(--destructive)","color","var(--destructive-foreground)","border","none"],s,s)
break
case 2:s=t.N
s=A.j([r,"var(--background)","color",q,"border","1px solid var(--input)"],s,s)
break
case 1:s=t.N
s=A.j([r,"var(--secondary)","color","var(--secondary-foreground)","border","none"],s,s)
break
case 3:s=t.N
s=A.j([r,"transparent","color",q,"border","none"],s,s)
break
case 5:s=t.N
s=A.j([r,"transparent","color","var(--primary)","border","none","text-underline-offset","4px","padding","0","height","auto"],s,s)
break
case 6:s=t.N
s=A.j([r,"var(--success, #22c55e)","color","var(--success-foreground, #ffffff)","border","none"],s,s)
break
case 7:s=t.N
s=A.j([r,"var(--warning, #f59e0b)","color","var(--warning-foreground, #000000)","border","none"],s,s)
break
case 8:s=t.N
s=A.j([r,"var(--info, #3b82f6)","color","var(--info-foreground, #ffffff)","border","none"],s,s)
break
case 9:s=t.N
s=A.j([r,"var(--accent)","color","var(--accent-foreground)","border","none"],s,s)
break
default:s=null}return s},
iE(a){var s
switch(a.a){case 0:s=t.N
s=A.j(["height","2.25rem","padding","0 0.75rem"],s,s)
break
case 1:s=t.N
s=A.j(["height","2.5rem","padding","0.5rem 1rem"],s,s)
break
case 2:s=t.N
s=A.j(["height","2.75rem","padding","0 2rem"],s,s)
break
case 3:s=t.N
s=A.j(["height","2.25rem","width","2.25rem","padding","0"],s,s)
break
case 4:s=t.N
s=A.j(["height","2.5rem","width","2.5rem","padding","0"],s,s)
break
case 5:s=t.N
s=A.j(["height","2.75rem","width","2.75rem","padding","0"],s,s)
break
default:s=null}return s}}
A.kW.prototype={
fi(a){var s,r="background-color",q="1px solid var(--border)",p="var(--shadow-xs)"
switch(a.c.a){case 0:s=t.N
s=A.j([r,"var(--card)","border",q,"box-shadow",p],s,s)
break
case 1:s=t.N
s=A.j([r,"var(--card)","border",q,"box-shadow","none"],s,s)
break
case 2:s=t.N
s=A.j([r,"var(--background)","border",q,"box-shadow","none"],s,s)
break
case 3:s=t.N
s=A.j([r,"transparent","border","none","box-shadow","none"],s,s)
break
case 4:s=t.N
s=A.j([r,"color-mix(in srgb, var(--card) 80%, transparent)","border",q,"box-shadow",p,"backdrop-filter","blur(8px)","-webkit-backdrop-filter","blur(8px)"],s,s)
break
case 5:s=t.N
s=A.j([r,"var(--card)","border",q,"box-shadow",p,"transition","all var(--transition)"],s,s)
break
default:s=null}return s}}
A.kX.prototype={
lV(a,b){var s,r,q,p,o,n,m,l,k,j,i=null
t.f.a(b)
switch(a.e.a){case 0:s="14px"
break
case 1:s="16px"
break
case 2:s="20px"
break
default:s=i}r=a.f.a
switch(r){case 0:q=B.ja
break
case 1:q=B.jf
break
case 2:q=B.iS
break
case 3:q=B.jh
break
case 4:q=B.iW
break
case 5:q=B.ji
break
default:q=i}p=q.a
o=i
n=q.b
o=n
m=p
switch(r){case 0:r="var(--primary-foreground)"
break
case 1:r="var(--secondary-foreground)"
break
case 2:r="var(--destructive-foreground)"
break
case 3:r="var(--success-foreground, #ffffff)"
break
case 4:r="var(--warning-foreground, #000000)"
break
case 5:r="var(--info-foreground, #ffffff)"
break
default:r=i}q=a.b
l=q?m:"transparent"
k=t.N
l=A.B(A.j(["width",s,"height",s,"border-radius","0.125rem","background-color",l,"border","1px solid "+A.w(o),"display","flex","align-items","center","justify-content","center","flex-shrink","0","transition","color var(--transition), background-color var(--transition), border-color var(--transition)"],k,k))
s=t.i
j=A.a([],s)
if(q){r=A.B(A.j(["color",r,"line-height","1"],k,k))
j.push(A.H(A.a([new A.a6("e06c",B.aP,i)],s),i,i,i,r))}return new A.c(i,"arcane-checkbox",l,b,i,j,i)}}
A.kY.prototype={}
A.kZ.prototype={
l(a){var s,r,q,p,o,n,m,l,k,j,i,h,g=null,f=$.B2+1
$.B2=f
s="arcane-dialog-"+f
f=t.N
r=A.ce(A.zs(g,g,g,!0,!0,!0,s,!1,!0,!0,"dialog"),f,f)
r.i(0,"data-arcane-scrim","")
q=t.v
p=A.t(f,q)
p.i(0,"click",new A.rI(this))
o=A.t(f,f)
o.i(0,"role","dialog")
o.i(0,"aria-modal","true")
n="dialog-title-"+s
o.i(0,"aria-labelledby",n)
o.i(0,"data-arcane-autofocus","")
m=this.c
l=A.B(A.j(["background-color","var(--background)","color","var(--foreground)","border-radius","var(--radius-md)","border","1px solid var(--border)","box-shadow","var(--shadow-lg)","max-width",""+m.w+"px","width","100%","max-height","calc(100vh - 48px)","display","flex","flex-direction","column","overflow","hidden","animation","arcane-scale-in var(--transition-slow)"],f,f))
k=A.j(["click",new A.rJ()],f,q)
j=t.i
i=A.a([],j)
h=A.a([],j)
h.push(A.H(A.a([new A.k(m.c,g)],j),g,g,n,B.lx))
n=A.t(f,f)
n.i(0,"type","button")
n.i(0,"aria-label","Close dialog")
n.B(0,A.j(["data-arcane-action","surface.dismiss"],f,f))
q=A.t(f,q)
q.i(0,"click",new A.rK(this))
h.push(A.fE(A.a([new A.a6("e1b2",B.a_,g)],j),n,"arcane-dialog-close",q,g,B.lV,g))
i.push(new A.c(g,"arcane-dialog-header",B.m8,g,g,h,g))
i.push(new A.c(g,"arcane-dialog-body",B.lE,g,g,m.d,g))
i.push(new A.c(g,"arcane-dialog-footer",B.ld,g,g,m.e,g))
return new A.c(g,"arcane-dialog-overlay",B.lb,r,p,A.a([new A.c(g,"arcane-dialog",l,o,k,i,g)],j),g)}}
A.rI.prototype={
$1(a){var s,r
A.p(a)
s=A.a7(a.target)
r=A.a7(a.currentTarget)
if(s==null?r==null:s===r)this.a.c.r.$0()},
$S:4}
A.rJ.prototype={
$1(a){return A.p(a).stopPropagation()},
$S:4}
A.rK.prototype={
$1(a){A.p(a)
return this.a.c.r.$0()},
$S:4}
A.l3.prototype={
gkk(){var s=this.c.d
return s===B.jN||s===B.jM},
glm(){var s,r=this.c.e
if(r===B.jO)return null
if(this.gkk()){s=null
switch(r.a){case 0:r=s
break
case 1:r="30vh"
break
case 2:r="50vh"
break
case 3:r="70vh"
break
case 4:r="90vh"
break
case 5:r="100vh"
break
default:r=s}return r}else{s=null
switch(r.a){case 0:r=s
break
case 1:r="280px"
break
case 2:r="400px"
break
case 3:r="540px"
break
case 4:r="720px"
break
case 5:r="100vw"
break
default:r=s}return r}},
gjh(){switch(this.c.d.a){case 3:var s="0.75rem 0.75rem 0 0"
break
case 2:s="0 0 0.75rem 0.75rem"
break
case 1:s="0.75rem 0 0 0.75rem"
break
case 0:s="0 0.75rem 0.75rem 0"
break
default:s=null}return s},
l(a0){var s,r,q,p,o,n,m,l,k,j,i,h,g=this,f=null,e="0",d="animation",c="max-height",b=g.c,a=$.B4+1
$.B4=a
s=g.glm()
r=b.e===B.jQ?e:g.gjh()
q=t.N
p=A.j(["position","fixed","z-index","50","background-color","var(--background)","color","var(--foreground)","box-shadow","var(--shadow-lg)","display","flex","flex-direction","column","overflow","hidden","transition","transform 300ms cubic-bezier(0.32, 0.72, 0, 1)","border-radius",r,"border","1px solid var(--border)"],q,q)
r=b.d
switch(r.a){case 1:p.B(0,A.j(["top","0","right","0","bottom","0","height","100%","width",s==null?"400px":s,"max-width","100vw","animation","arcane-slide-left var(--transition-slower)"],q,q))
break
case 0:p.B(0,A.j(["top","0","left","0","bottom","0","height","100%","width",s==null?"400px":s,"max-width","100vw","animation","arcane-slide-right var(--transition-slower)"],q,q))
break
case 3:o=A.t(q,q)
o.i(0,"left",e)
o.i(0,"right",e)
o.i(0,"bottom",e)
if(s!=null)o.i(0,"height",s)
o.i(0,c,"90vh")
o.i(0,"width","100%")
o.i(0,d,"arcane-slide-up var(--transition-slower)")
p.B(0,o)
break
case 2:o=A.t(q,q)
o.i(0,"left",e)
o.i(0,"right",e)
o.i(0,"top",e)
if(s!=null)o.i(0,"height",s)
o.i(0,c,"90vh")
o.i(0,d,"arcane-slide-down var(--transition-slower)")
p.B(0,o)
break}a=A.ce(A.zs(f,f,f,!0,!0,!0,"arcane-sheet-"+a,b.b,!0,!0,"sheet"),q,q)
r=r.b
a.i(0,"data-position",r)
o=t.i
n=A.a([],o)
m=A.j(["data-arcane-scrim",""],q,q)
l=t.v
k=A.t(q,l)
k.i(0,"click",new A.rL(g))
n.push(new A.c(f,"arcane-sheet-backdrop",B.kZ,m,k,A.a([],o),f))
m=A.B(p)
k=A.j(["click",new A.rM()],q,l)
j=A.a([],o)
i=A.a([],o)
h=b.w
if(h!=null){h=A.a([A.H(A.a([new A.k(h,f)],o),f,f,f,B.m0)],o)
i.push(new A.c(f,f,B.m1,f,f,h,f))}else i.push(B.mc)
h=A.t(q,q)
h.i(0,"type","button")
h.i(0,"aria-label","Close sheet")
h.B(0,A.j(["data-arcane-action","surface.dismiss"],q,q))
l=A.t(q,l)
l.i(0,"click",new A.rN(g))
i.push(A.fE(A.a([new A.a6("e1b2",B.a_,f)],o),h,"arcane-sheet-close",l,f,B.lr,f))
j.push(new A.c(f,"arcane-sheet-header",B.kr,f,f,i,f))
j.push(new A.c(f,"arcane-sheet-content",B.kY,f,f,A.a([b.c],o),f))
n.push(new A.c(f,"arcane-sheet-panel arcane-sheet-"+r,m,B.er,k,j,f))
return new A.c(f,"arcane-sheet",B.kt,a,f,n,f)}}
A.rL.prototype={
$1(a){A.p(a)
return this.a.c.f.$0()},
$S:4}
A.rM.prototype={
$1(a){return A.p(a).stopPropagation()},
$S:4}
A.rN.prototype={
$1(a){A.p(a)
return this.a.c.f.$0()},
$S:4}
A.l_.prototype={
geA(){switch(this.c.r.a){case 0:var s=B.jB
break
case 1:s=B.jC
break
case 2:s=B.jA
break
default:s=null}return s},
lW(a){var s=null
if(this.c.f===B.cC)return new A.c(s,"arcane-empty-state-card",B.l7,s,s,A.a([a],t.i),s)
return a}}
A.l0.prototype={}
A.l1.prototype={}
A.l2.prototype={}
A.l4.prototype={
l(a){var s,r,q="collapsed",p=null,o=this.c,n=o.d,m=n?o.r:o.f,l=n?q:"",k=n?q:"expanded",j=t.N
k=A.j(["data-state",k],j,j)
j=A.B(A.j(["display","flex","flex-direction","column","width",""+m+"px","height","100%","background-color","var(--background)","border-right","1px solid var(--border)","transition","width var(--transition-slow)","flex-shrink","0","overflow","hidden"],j,j))
s=t.i
r=A.a([],s)
r.push(new A.c(p,"sidebar-header",p,p,p,A.a([o.b],s),p))
r.push(A.I7(o.a,"sidebar-nav",B.kU))
s=A.a([],s)
if(!n)s.push(o.c)
r.push(new A.c(p,"arcane-sidebar-footer",B.lp,p,p,s,p))
return A.zf(r,k,"arcane-sidebar "+l+" left",j)}}
A.l5.prototype={
f4(a){var s,r=a.c
A:{s="var(--primary)"
if(B.a7===r)break A
if(B.a8===r)break A
if(B.a9===r){s="var(--success)"
break A}break A}return s},
hL(a){var s="6px"
switch(a.b.a){case 0:break
case 1:break
case 2:s="8px"
break
default:s=null}return s},
hk(a){var s
switch(a.b.a){case 0:s="0.25rem 0.5rem"
break
case 1:s="0.25rem 0.75rem"
break
case 2:s="0.375rem 1rem"
break
default:s=null}return s},
hS(a,b){var s=t.N
return A.j(["font-size",this.iI(a),"font-weight","500","color",b,"white-space","nowrap"],s,s)},
jw(a){var s
switch(a.b.a){case 0:s="0.125rem 0.5rem"
break
case 1:s="0.125rem 0.625rem"
break
case 2:s="0.25rem 0.75rem"
break
default:s=null}return s},
jv(a){var s="0.75rem"
switch(a.b.a){case 0:break
case 1:break
case 2:s="0.875rem"
break
default:s=null}return s},
ju(a){var s,r=a.c
A:{if(B.a7===r||B.aw===r){s=B.br
break A}if(B.a8===r){s=B.br
break A}if(B.a9===r||B.ax===r){s=B.jw
break A}if(B.ay===r){s=B.ju
break A}if(B.az===r){s=B.jv
break A}if(B.aA===r){s=B.jy
break A}if(B.av===r){s=B.jx
break A}if(B.aa===r){s=B.bq
break A}if(B.o===r){s=B.bq
break A}s=null}return s}}
A.l7.prototype={
iF(a){var s
switch(a.a){case 0:s=B.jz
break
case 1:s=B.js
break
case 2:s=B.jt
break
default:s=null}return s}}
A.l8.prototype={
jl(){var s,r,q=null,p=this.c.d
switch(p.a){case 1:s=new A.a6("e226",B.f,q)
break
case 3:s=new A.a6("e084",B.f,q)
break
case 2:s=new A.a6("e193",B.f,q)
break
case 0:s=new A.a6("e0f9",B.f,q)
break
case 4:s=new A.a6("e109",B.f,q)
break
default:s=q}r=t.N
r=A.t(r,r)
r.i(0,"color",this.k6())
if(p===B.jY)r.i(0,"animation","arcane-toast-spin 1s linear infinite")
return new A.c(q,q,A.B(r),q,q,A.a([s],t.i),q)},
k6(){switch(this.c.d.a){case 1:var s="var(--success)"
break
case 3:s="var(--destructive)"
break
case 2:s="var(--warning)"
break
case 0:s="var(--info)"
break
case 4:s="var(--primary)"
break
default:s=null}return s},
k9(){switch(this.c.d.a){case 1:var s="var(--success)"
break
case 3:s="var(--destructive)"
break
case 2:s="var(--warning)"
break
case 0:s="var(--info)"
break
case 4:s="var(--primary)"
break
default:s=null}return s},
l(a){var s,r,q,p,o,n,m,l,k=null,j="var(--foreground)",i=this.c,h=i.d,g=h.b
h=h===B.an?"assertive":"polite"
s=i.f
r=""+s
q=t.N
h=A.j(["role","alert","aria-live",h,"aria-atomic","true","data-variant",g,"data-duration",r,"data-dismissible","true","data-position",i.e.b,"data-state","open"],q,q)
p=A.B(A.j(["display","flex","align-items","flex-start","gap","16px","padding","24px","padding-right","32px","background-color","var(--background)","color",j,"border","1px solid var(--border)","border-radius","var(--radius-sm)","box-shadow",u.E,"min-width","320px","max-width","420px","pointer-events","auto","position","relative","overflow","hidden","transition","all 200ms cubic-bezier(0.4, 0, 0.2, 1)","animation","arcane-toast-enter 300ms cubic-bezier(0, 0, 0.2, 1) forwards"],q,q))
o=t.i
n=A.a([this.jl()],o)
m=A.a([],o)
l=A.B(A.j(["font-size","var(--font-size-sm)","color",j,"line-height","1.5"],q,q))
m.push(A.H(A.a([new A.k(i.a,k)],o),k,"arcane-toast-message",k,l))
l=i.c
if(l!=null)m.push(A.H(A.a([new A.k(l,k)],o),k,"arcane-toast-description",k,B.lH))
n=A.a([new A.c(k,"arcane-toast-icon",B.lY,k,k,n,k),new A.c(k,"arcane-toast-content",B.li,k,k,m,k)],o)
if(s>0)n.push(new A.c(k,"arcane-toast-progress",A.B(A.j(["position","absolute","bottom","0","left","0","height","2px","background",this.k9(),"border-radius","0 0 0 0.5rem","animation","arcane-toast-progress "+r+"ms linear forwards"],q,q)),k,k,A.a([],o),k))
return new A.c("toast-"+i.y,"arcane-toast arcane-toast-"+g,p,h,A.t(q,t.v),n,k)}}
A.l9.prototype={
k7(){var s="translateX(-50%)",r=this.c,q=""+r.d+"px"
switch(r.a.a){case 0:r=t.N
r=A.j(["top",q,"left",q,"align-items","flex-start","flex-direction","column"],r,r)
break
case 1:r=t.N
r=A.j(["top",q,"left","50%","transform",s,"align-items","center","flex-direction","column"],r,r)
break
case 2:r=t.N
r=A.j(["top",q,"right",q,"align-items","flex-end","flex-direction","column"],r,r)
break
case 3:r=t.N
r=A.j(["bottom",q,"left",q,"align-items","flex-start","flex-direction","column-reverse"],r,r)
break
case 4:r=t.N
r=A.j(["bottom",q,"left","50%","transform",s,"align-items","center","flex-direction","column-reverse"],r,r)
break
case 5:r=t.N
r=A.j(["bottom",q,"right",q,"align-items","flex-end","flex-direction","column-reverse"],r,r)
break
default:r=null}return r},
l(a){var s,r,q,p,o=this.c,n=t.N,m=A.j(["role","region","aria-label","Notifications","data-position",o.a.b],n,n)
n=A.t(n,n)
n.i(0,"position","fixed")
n.i(0,"z-index","100")
n.i(0,"display","flex")
n.i(0,"gap",""+o.c+"px")
n.i(0,"pointer-events","none")
n.i(0,"max-height","calc(100vh - 40px)")
n.i(0,"overflow","visible")
n.B(0,this.k7())
n=A.B(n)
s=A.a([],t.i)
for(r=o.e,r=A.e4(r,0,A.fF(o.b,"count",t.S),A.F(r).c),o=r.$ti,r=new A.aw(r,r.gn(0),o.h("aw<z.E>")),q=t.le,o=o.h("z.E");r.p();){p=r.d
if(p==null)p=o.a(p)
s.push(new A.l8(p,new A.e8(p.y,q)))}return new A.c(null,"arcane-toaster",n,m,null,s,null)}}
A.la.prototype={
lX(b0,b1){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e,d,c,b,a,a0,a1,a2,a3,a4=null,a5="Pattern matching error",a6="disabled",a7="unchecked",a8="var(--radius-full)",a9="background-color"
t.f.a(b1)
switch(b0.f.a){case 0:s=B.jp
break
case 1:s=B.jq
break
case 2:s=B.jr
break
default:s=a4}s=s.a
r=s[0]
q=a4
p=a4
o=a4
n=s[1]
m=s[2]
l=s[3]
o=l
p=m
q=n
k=r
s=b0.b
if(s){if(typeof k!=="number")return k.fo()
if(typeof p!=="number")return A.zk(p)
if(typeof o!=="number")return o.aB()
j=k-p-o*2}else j=0
switch(b0.r.a){case 0:i=B.j2
break
case 1:i=B.j8
break
case 2:i=B.je
break
case 3:i=B.jo
break
case 4:i=B.jd
break
case 5:i=B.j4
break
default:i=a4}h=i.a
g=a4
f=i.b
g=f
e=h
i=s?"active":""
d=b0.e
c=d?a6:""
b=t.N
a=A.t(b,b)
a.i(0,"type","button")
a.i(0,"role","switch")
a.i(0,"aria-checked",B.d6.k(s))
if(d)a.i(0,a6,"true")
a.i(0,"data-state",s?"checked":a7)
a.i(0,"data-disabled",""+d)
a=A.nf(A.a([a,b1],t.gm))
a0=s?"none":"1px solid var(--border)"
a1=s?e:g
a2=d?"not-allowed":"pointer"
a3=d?"0.5":"1"
d=d?"none":"auto"
d=A.B(A.j(["position","relative","display","inline-flex","align-items","center","flex-shrink","0","width",A.w(k)+"px","height",A.w(q)+"px","padding",A.w(o)+"px","border",a0,"border-radius",a8,a9,a1,"cursor",a2,"opacity",a3,"pointer-events",d,"transition","background-color var(--transition), border-color var(--transition)","outline","none","box-sizing","border-box"],b,b))
a3=A.j(["click",new A.rP(b0)],b,t.v)
a0=A.j(["data-state",s?"checked":a7],b,b)
a1=A.w(p)+"px"
b=A.B(A.j(["display","block","width",a1,"height",a1,"border-radius",a8,a9,"var(--background)","box-shadow",u.E,"transform","translateX("+j+"px)","transition","transform var(--transition)","pointer-events","none","flex-shrink","0"],b,b))
a1=t.i
return A.fE(A.a([A.H(A.a([],a1),a0,"arcane-toggle-thumb",a4,b)],a1),a,"arcane-toggle-switch "+i+" "+c,a3,a4,d,a4)}}
A.rP.prototype={
$1(a){var s
A.p(a)
s=this.a
if(!s.e&&s.c!=null)s.c.$1(!s.b)},
$S:4}
A.l6.prototype={
gmH(){var s=null
return new A.lx(4278782219,4294967295,s,s,s,4293870660,4280468830,4294286859,4282090230,!1,!1,s)}}
A.rO.prototype={
E(){return"ShadcnTheme."+this.b}}
A.U.prototype={
j(a,b){var s,r=this
if(!r.ek(b))return null
s=r.c.j(0,r.a.$1(r.$ti.h("U.K").a(b)))
return s==null?null:s.b},
i(a,b,c){var s=this,r=s.$ti
r.h("U.K").a(b)
r.h("U.V").a(c)
if(!s.ek(b))return
s.c.i(0,s.a.$1(b),new A.W(b,c,r.h("W<U.K,U.V>")))},
B(a,b){this.$ti.h("L<U.K,U.V>").a(b).aa(0,new A.o8(this))},
K(a){var s=this
if(!s.ek(a))return!1
return s.c.K(s.a.$1(s.$ti.h("U.K").a(a)))},
gaF(){var s=this.c,r=A.n(s).h("aC<1,2>"),q=this.$ti.h("W<U.K,U.V>")
return A.qs(new A.aC(s,r),r.A(q).h("1(m.E)").a(new A.o9(this)),r.h("m.E"),q)},
aa(a,b){this.c.aa(0,new A.oa(this,this.$ti.h("~(U.K,U.V)").a(b)))},
gL(a){return this.c.a===0},
ga1(a){return this.c.a!==0},
ga9(){var s=this.c,r=A.n(s).h("cI<2>"),q=this.$ti.h("U.K")
return A.qs(new A.cI(s,r),r.A(q).h("1(m.E)").a(new A.ob(this)),r.h("m.E"),q)},
gn(a){return this.c.a},
bs(a,b,c,d){return this.c.bs(0,new A.oc(this,this.$ti.A(c).A(d).h("W<1,2>(U.K,U.V)").a(b),c,d),c,d)},
k(a){return A.qq(this)},
ek(a){return this.$ti.h("U.K").b(a)},
$iL:1}
A.o8.prototype={
$2(a,b){var s=this.a,r=s.$ti
r.h("U.K").a(a)
r.h("U.V").a(b)
s.i(0,a,b)
return b},
$S(){return this.a.$ti.h("~(U.K,U.V)")}}
A.o9.prototype={
$1(a){var s=this.a.$ti,r=s.h("W<U.C,W<U.K,U.V>>").a(a).b
return new A.W(r.a,r.b,s.h("W<U.K,U.V>"))},
$S(){return this.a.$ti.h("W<U.K,U.V>(W<U.C,W<U.K,U.V>>)")}}
A.oa.prototype={
$2(a,b){var s=this.a.$ti
s.h("U.C").a(a)
s.h("W<U.K,U.V>").a(b)
return this.b.$2(b.a,b.b)},
$S(){return this.a.$ti.h("~(U.C,W<U.K,U.V>)")}}
A.ob.prototype={
$1(a){return this.a.$ti.h("W<U.K,U.V>").a(a).a},
$S(){return this.a.$ti.h("U.K(W<U.K,U.V>)")}}
A.oc.prototype={
$2(a,b){var s=this.a.$ti
s.h("U.C").a(a)
s.h("W<U.K,U.V>").a(b)
return this.b.$2(b.a,b.b)},
$S(){return this.a.$ti.A(this.c).A(this.d).h("W<1,2>(U.C,W<U.K,U.V>)")}}
A.kN.prototype={}
A.jk.prototype={
bD(a,b,c,d,e){return this.l5(a,b,t.t.a(c),d,e)},
l4(a,b,c){return this.bD(a,b,c,null,null)},
l5(a,b,c,d,e){var s=0,r=A.Q(t.cD),q,p=this,o,n
var $async$bD=A.R(function(f,g){if(f===1)return A.N(g,r)
for(;;)switch(s){case 0:o=A.EI(a,b)
o.r.B(0,c)
if(d!=null)o.saI(d)
n=A
s=3
return A.G(p.bY(o),$async$bD)
case 3:q=n.re(g)
s=1
break
case 1:return A.O(q,r)}})
return A.P($async$bD,r)},
$iAi:1}
A.fS.prototype={
bc(){if(this.w)throw A.d(A.cU("Can't finalize a finalized Request."))
this.w=!0
return B.ce},
k(a){return this.a+" "+this.b.k(0)}}
A.nZ.prototype={
$2(a,b){return A.r(a).toLowerCase()===A.r(b).toLowerCase()},
$S:107}
A.o_.prototype={
$1(a){return B.a.gI(A.r(a).toLowerCase())},
$S:106}
A.o0.prototype={
fu(a,b,c,d,e,f,g){var s=this.b
if(s<100)throw A.d(A.ai("Invalid status code "+s+".",null))
else{s=this.d
if(s!=null&&s<0)throw A.d(A.ai("Invalid content length "+A.w(s)+".",null))}}}
A.jl.prototype={
bY(a){return this.ip(a)},
ip(b5){var s=0,r=A.Q(t.hL),q,p=2,o=[],n=[],m=this,l,k,j,i,h,g,f,e,d,c,b,a,a0,a1,a2,a3,a4,a5,a6,a7,a8,a9,b0,b1,b2,b3,b4
var $async$bY=A.R(function(b6,b7){if(b6===1){o.push(b7)
s=p}for(;;)switch(s){case 0:b1=v.G
b2=A.p(new b1.AbortController())
b3=m.c
B.b.m(b3,b2)
b5.iJ()
a3=t.oU
a4=new A.dt(null,null,null,null,a3)
a4.dZ(b5.y)
a4.fF()
s=3
return A.G(new A.ey(new A.dv(a4,a3.h("dv<1>"))).ia(),$async$bY)
case 3:l=b7
p=5
k=b5
j=null
i=!1
h=null
a3=b5.b
a5=a3.k(0)
a4=!J.eq(l)?l:null
a6=t.N
g=A.t(a6,t.K)
f=b5.y.length
e=null
if(f!=null){e=f
J.d9(g,"content-length",e)}for(a7=b5.r,a7=new A.aC(a7,A.n(a7).h("aC<1,2>")).gC(0);a7.p();){a8=a7.d
a8.toString
d=a8
J.d9(g,d.a,d.b)}g=A.zo(g)
g.toString
A.p(g)
a7=A.p(b2.signal)
s=8
return A.G(A.nk(A.p(b1.fetch(a5,{method:b5.a,headers:g,body:a4,credentials:"same-origin",redirect:"follow",signal:a7})),t.m),$async$bY)
case 8:c=b7
b=A.aA(A.p(c.headers).get("content-length"))
a=b!=null?A.hH(b,null):null
if(a==null&&b!=null){g=A.Dx("Invalid content-length header ["+b+"].",a3)
throw A.d(g)}a0=A.t(a6,a6)
g=A.p(c.headers)
b1=new A.o2(a0)
if(typeof b1=="function")A.ak(A.ai("Attempting to rewrap a JS function.",null))
a9=function(b8,b9){return function(c0,c1,c2){return b8(b9,c0,c1,c2,arguments.length)}}(A.G8,b1)
a9[$.yk()]=b1
g.forEach(a9)
g=A.G3(b5,c)
b1=A.bb(c.status)
a3=a0
a4=a
A.bN(A.r(c.url))
a6=A.r(c.statusText)
g=new A.ll(A.Il(g),b5,b1,a6,a4,a3,!1,!0)
g.fu(b1,a4,a3,!1,!0,a6,b5)
q=g
n=[1]
s=6
break
n.push(7)
s=6
break
case 5:p=4
b4=o.pop()
a1=A.a1(b4)
a2=A.b3(b4)
A.Cc(a1,a2,b5)
n.push(7)
s=6
break
case 4:n=[2]
case 6:p=2
B.b.J(b3,b2)
s=n.pop()
break
case 7:case 1:return A.O(q,r)
case 2:return A.N(o.at(-1),r)}})
return A.P($async$bY,r)}}
A.o2.prototype={
$3(a,b,c){A.r(a)
this.a.i(0,A.r(b).toLowerCase(),a)},
$2(a,b){return this.$3(a,b,null)},
$S:103}
A.xc.prototype={
$1(a){return A.fz(this.a,this.b,t.o1.a(a))},
$S:99}
A.xm.prototype={
$0(){var s=this.a,r=s.a
if(r!=null){s.a=null
r.m2()}},
$S:0}
A.xn.prototype={
$0(){var s=0,r=A.Q(t.H),q=1,p=[],o=this,n,m,l,k
var $async$$0=A.R(function(a,b){if(a===1){p.push(b)
s=q}for(;;)switch(s){case 0:q=3
o.a.c=!0
s=6
return A.G(A.nk(A.p(o.b.cancel()),t.X),$async$$0)
case 6:q=1
s=5
break
case 3:q=2
k=p.pop()
n=A.a1(k)
m=A.b3(k)
if(!o.a.b)A.Cc(n,m,o.c)
s=5
break
case 2:s=1
break
case 5:return A.O(null,r)
case 1:return A.N(p.at(-1),r)}})
return A.P($async$$0,r)},
$S:31}
A.ey.prototype={
ia(){var s=new A.a_($.a0,t.jz),r=new A.c3(s,t.iq),q=new A.lW(new A.o7(r),new Uint8Array(1024))
this.aY(t.nw.a(q.glO(q)),!0,q.gm_(),r.gm3())
return s}}
A.o7.prototype={
$1(a){return this.a.ba(new Uint8Array(A.C_(t.L.a(a))))},
$S:97}
A.c8.prototype={
k(a){var s=this.b.k(0)
return"ClientException: "+this.a+", uri="+s},
$iaj:1}
A.kM.prototype={
geP(){var s,r,q=this
if(q.gb6()==null||!q.gb6().c.a.K("charset"))return q.x
s=q.gb6().c.a.j(0,"charset")
s.toString
r=A.Aq(s)
return r==null?A.ak(A.ap('Unsupported encoding "'+s+'".',null,null)):r},
saI(a){var s,r,q=this,p=t.L.a(q.geP().di(a))
q.jx()
q.y=A.CO(p)
s=q.gb6()
if(s==null){p=t.N
q.sb6(A.qt("text","plain",A.j(["charset",q.geP().gbe()],p,p)))}else{p=q.gb6()
if(p!=null){r=p.a
if(r!=="text"){p=r+"/"+p.b
p=p==="application/xml"||p==="application/xml-external-parsed-entity"||p==="application/xml-dtd"||B.a.a8(p,"+xml")}else p=!0}else p=!1
if(p&&!s.c.a.K("charset")){p=t.N
q.sb6(s.lZ(A.j(["charset",q.geP().gbe()],p,p)))}}},
gb6(){var s=this.r.j(0,"content-type")
if(s==null)return null
return A.AC(s)},
sb6(a){this.r.i(0,"content-type",a.k(0))},
jx(){if(!this.w)return
throw A.d(A.cU("Can't modify a finalized Request."))}}
A.kO.prototype={
gaI(){return A.bt(A.bs(this.e)).a7(this.w)}}
A.hU.prototype={}
A.ll.prototype={}
A.fX.prototype={}
A.eW.prototype={
lZ(a){var s,r
t.t.a(a)
s=t.N
r=A.qe(this.c,s,s)
r.B(0,a)
return A.qt(this.a,this.b,r)},
k(a){var s=new A.aI(""),r=this.a
s.a=r
r+="/"
s.a=r
s.a=r+this.b
r=this.c
r.a.aa(0,r.$ti.h("~(1,2)").a(new A.qw(s)))
r=s.a
return r.charCodeAt(0)==0?r:r}}
A.qu.prototype={
$0(){var s,r,q,p,o,n,m,l,k,j=this.a,i=new A.t1(null,j),h=$.Dk()
i.dP(h)
s=$.Dj()
i.cf(s)
r=i.geY().j(0,0)
r.toString
i.cf("/")
i.cf(s)
q=i.geY().j(0,0)
q.toString
i.dP(h)
p=t.N
o=A.t(p,p)
for(;;){p=i.d=B.a.bt(";",j,i.c)
n=i.e=i.c
m=p!=null
p=m?i.e=i.c=p.gF():n
if(!m)break
p=i.d=h.bt(0,j,p)
i.e=i.c
if(p!=null)i.e=i.c=p.gF()
i.cf(s)
if(i.c!==i.e)i.d=null
p=i.d.j(0,0)
p.toString
i.cf("=")
n=i.d=s.bt(0,j,i.c)
l=i.e=i.c
m=n!=null
if(m){n=i.e=i.c=n.gF()
l=n}else n=l
if(m){if(n!==l)i.d=null
n=i.d.j(0,0)
n.toString
k=n}else k=A.Hl(i)
n=i.d=h.bt(0,j,i.c)
i.e=i.c
if(n!=null)i.e=i.c=n.gF()
o.i(0,p,k)}i.mn()
return A.qt(r,q,o)},
$S:96}
A.qw.prototype={
$2(a,b){var s,r,q
A.r(a)
A.r(b)
s=this.a
s.a+="; "+a+"="
r=$.Dh()
r=r.b.test(b)
q=s.a
if(r){s.a=q+'"'
r=A.CM(b,$.Db(),t.jt.a(t.po.a(new A.qv())),null)
s.a=(s.a+=r)+'"'}else s.a=q+b},
$S:95}
A.qv.prototype={
$1(a){return"\\"+A.w(a.j(0,0))},
$S:22}
A.xY.prototype={
$1(a){var s=a.j(0,1)
s.toString
return s},
$S:22}
A.fZ.prototype={
ghD(){var s,r=$.CS().length,q=v.G
if(r>A.r(A.p(A.p(q.window).location).href).length)return"/"
s=B.a.S(A.r(A.p(A.p(q.window).location).href),r)
return!B.a.M(s,"/")?"/"+s:s},
m8(){var s=A.p(v.G.document),r=this.c
r===$&&A.S()
r=A.a7(s.querySelector(r))
r.toString
r=A.EK(r,null)
return r},
eL(){this.c$.d$.bc()
this.iZ()},
i5(a,b,c){t.l.a(c)
A.p(v.G.console).error("Error while building "+A.bH(a.gD()).k(0)+":\n"+A.w(b)+"\n\n"+c.k(0))}}
A.og.prototype={
$0(){var s=v.G,r=A.a7(A.p(s.document).querySelector("head>base")),q=r==null?null:A.r(r.href)
return q==null?A.r(A.p(A.p(s.window).location).origin):q},
$S:94}
A.lX.prototype={}
A.bW.prototype={
smX(a){this.a=t.n2.a(a)},
smR(a){this.c=t.n2.a(a)},
$if5:1}
A.jH.prototype={
gae(){var s=this.d
s===$&&A.S()
return s},
cQ(a){var s,r,q=this,p=B.fE.j(0,a)
if(p==null){s=q.a
if(s==null)s=null
else s=s.gae() instanceof $.yl()
s=s===!0}else s=!1
if(s){s=q.a
s=s==null?null:s.gae()
if(s==null)s=A.p(s)
p=A.aA(s.namespaceURI)}s=q.a
r=s==null?null:s.dE(new A.oL(a))
if(r!=null){q.d!==$&&A.bT()
q.d=r
s=A.qB(A.p(r.childNodes))
s=A.x(s,s.$ti.h("m.E"))
q.k3$=s
return}s=q.jO(a,p)
q.d!==$&&A.bT()
q.d=s},
jO(a,b){if(b!=null&&b!=="http://www.w3.org/1999/xhtml")return A.p(A.p(v.G.document).createElementNS(b,a))
return A.p(A.p(v.G.document).createElement(a))},
ib(a,b,c,a0,a1){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e=this,d=t.t
d.a(c)
d.a(a0)
t.oq.a(a1)
d=t.N
s=A.cJ(d)
r=0
for(;;){q=e.d
q===$&&A.S()
if(!(r<A.bb(A.p(q.attributes).length)))break
s.m(0,A.r(A.a7(A.p(q.attributes).item(r)).name));++r}A.nX(q,"id",a)
A.nX(q,"class",b==null||b.length===0?null:b)
A.nX(q,"style",c==null||c.gL(c)?null:c.gaF().aZ(0,new A.oM(),d).aA(0,"; "))
p=a0==null
if(!p&&a0.ga1(a0))for(o=a0.gaF(),o=o.gC(o);o.p();){n=o.gu()
m=n.a
l=n.b
if(m==="value"){n=q instanceof $.D9()
if(n){if(A.r(q.value)!==l)q.value=l
continue}n=q instanceof $.ym()
if(n){if(A.r(q.value)!==l)q.value=l
continue}}else if(m==="checked"){n=q instanceof $.ym()
if(n){k=A.r(q.type)
if("checkbox"===k||"radio"===k){j=l==="true"
if(A.dz(q.checked)!==j){q.checked=j
if(!j&&A.dz(q.hasAttribute("checked")))q.removeAttribute("checked")}continue}}}else if(m==="indeterminate"){n=q instanceof $.ym()
if(n)if(A.r(q.type)==="checkbox"){i=l==="true"
if(A.dz(q.indeterminate)!==i){q.indeterminate=i
if(!i&&A.dz(q.hasAttribute("indeterminate")))q.removeAttribute("indeterminate")}continue}}A.nX(q,m,l)}o=A.Eo(["id","class","style"],t.X)
p=p?null:a0.ga9()
if(p!=null)o.B(0,p)
h=s.cd(o)
for(s=h.gC(h);s.p();)q.removeAttribute(s.gu())
s=a1!=null&&a1.ga1(a1)
g=e.e
if(s){if(g==null)g=e.e=A.t(d,t.lL)
d=A.n(g).h("aW<1>")
f=A.yO(new A.aW(g,d),d.h("m.E"))
a1.aa(0,new A.oN(e,f,g))
for(d=A.Bq(f,f.r,A.n(f).c),s=d.$ti.c;d.p();){q=d.d
q=g.J(0,q==null?s.a(q):q)
if(q!=null){p=q.c
if(p!=null)p.W()
q.c=null}}}else if(g!=null){for(d=new A.bh(g,g.r,g.e,A.n(g).h("bh<2>"));d.p();){s=d.d
q=s.c
if(q!=null)q.W()
s.c=null}e.e=null}},
bH(a,b){this.lS(a,b)},
J(a,b){this.f6(b)},
$iAY:1}
A.oL.prototype={
$1(a){var s=a instanceof $.yl()
return s&&A.r(a.tagName).toLowerCase()===this.a},
$S:40}
A.oM.prototype={
$1(a){t.gc.a(a)
return a.a+": "+a.b},
$S:41}
A.oN.prototype={
$2(a,b){var s,r,q
A.r(a)
t.v.a(b)
this.b.J(0,a)
s=this.c
r=s.j(0,a)
if(r!=null)r.smw(b)
else{q=this.a.d
q===$&&A.S()
s.i(0,a,A.DR(q,a,b))}},
$S:88}
A.h7.prototype={
gae(){var s=this.d
s===$&&A.S()
return s},
cQ(a){var s=this,r=s.a,q=r==null?null:r.dE(new A.oO())
if(q!=null){s.d!==$&&A.bT()
s.d=q
if(A.aA(q.textContent)!==a)q.textContent=a
return}r=A.p(new v.G.Text(a))
s.d!==$&&A.bT()
s.d=r},
bH(a,b){throw A.d(A.ao("Text nodes cannot have children attached to them."))},
J(a,b){throw A.d(A.ao(u.x))},
dE(a){t.bD.a(a)
return null},
bc(){},
$iyS:1}
A.oO.prototype={
$1(a){var s=a instanceof $.Da()
return s},
$S:40}
A.bV.prototype={
gbL(){var s=this.f
if(s!=null){if(s instanceof A.bV)return s.gcj()
return s.gae()}return null},
gcj(){var s=this.r
if(s!=null){if(s instanceof A.bV)return s.gcj()
return s.gae()}return null},
bH(a,b){var s=this,r=s.gbL()
s.eG(a,b,r==null?null:A.a7(r.previousSibling))
if(b==null)s.f=a
if(b==s.r)s.r=a},
mP(a,b,c){var s,r,q,p,o=this.gbL()
if(o==null)return
s=A.a7(o.previousSibling)
if((s==null?c==null:s===c)&&A.a7(o.parentNode)===b)return
r=this.gcj()
q=c==null?A.a7(A.p(b.childNodes).item(0)):A.a7(c.nextSibling)
for(;r!=null;q=r,r=p){p=r!==this.gbL()?A.a7(r.previousSibling):null
A.p(b.insertBefore(r,q))}},
n5(a){var s,r,q,p,o=this
if(o.gbL()==null)return
s=o.gcj()
for(r=o.d,q=null;s!=null;q=s,s=p){p=s!==o.gbL()?A.a7(s.previousSibling):null
A.p(r.insertBefore(s,q))}o.e=!1},
J(a,b){var s=this
if(b===s.f)s.f=b.c
if(b===s.r)s.r=b.b
if(!s.e)s.f6(b)
else s.a.J(0,b)},
bc(){this.e=!0},
$iAZ:1,
gae(){return this.d}}
A.kP.prototype={
bH(a,b){var s=this.e
s===$&&A.S()
this.eG(a,b,s)},
J(a,b){this.f6(b)},
gae(){return this.d}}
A.cK.prototype={
ghz(){var s=this
if(s instanceof A.bV&&s.e)return t.mV.a(s.a).ghz()
return s.gae()},
dO(a){var s,r=this
if(a instanceof A.bV){s=a.gcj()
if(s!=null)return s
else return r.dO(a.b)}if(a!=null)return a.gae()
if(r instanceof A.bV&&r.e)return t.mV.a(r.a).dO(r.b)
return null},
eG(a,b,c){var s,r,q,p,o,n,m,l,k=this
a.smX(k)
s=k.ghz()
o=k.dO(b)
r=o==null?c:o
n=a instanceof A.bV
if(n&&a.e){a.mP(k,s,r)
return}try{q=a.gae()
m=A.a7(q.previousSibling)
l=r
if(m==null?l==null:m===l){m=A.a7(q.parentNode)
l=s
l=m==null?l==null:m===l
m=l}else m=!1
if(m)return
if(r==null)A.p(s.insertBefore(q,A.a7(A.p(s.childNodes).item(0))))
else A.p(s.insertBefore(q,A.a7(r.nextSibling)))
if(n)a.gbL()
n=b==null
p=n?null:b.c
a.b=b
if(!n)b.c=a
a.smR(p)
n=p
if(n!=null)n.b=a}finally{a.bc()}},
lS(a,b){return this.eG(a,b,null)},
f6(a){var s,r
if(a instanceof A.bV&&a.e)a.n5(this)
else A.p(this.gae().removeChild(a.gae()))
s=a.b
r=a.c
if(s!=null)s.c=r
if(r!=null)r.b=s
a.a=a.c=a.b=null}}
A.cD.prototype={
dE(a){var s,r,q,p
t.bD.a(a)
s=this.k3$
r=s.length
if(r!==0)for(q=0;q<s.length;s.length===r||(0,A.I)(s),++q){p=s[q]
if(a.$1(p)){B.b.J(this.k3$,p)
return p}}return null},
bc(){var s,r,q,p
for(s=this.k3$,r=s.length,q=0;q<s.length;s.length===r||(0,A.I)(s),++q){p=s[q]
A.p(A.a7(p.parentNode).removeChild(p))}B.b.O(this.k3$)}}
A.jO.prototype={
j1(a,b,c){var s=t.gX
this.c=A.yZ(a,this.a,s.h("~(1)?").a(new A.oW(this)),!1,s.c)},
smw(a){this.b=t.v.a(a)}}
A.oW.prototype={
$1(a){this.a.b.$1(a)},
$S:4}
A.m1.prototype={}
A.m2.prototype={}
A.m3.prototype={}
A.m4.prototype={}
A.mA.prototype={}
A.mB.prototype={}
A.fU.prototype={
l(a){return this.c.$1(a)}}
A.hh.prototype={
l(a){var s=null,r=t.i,q=A.a([],r),p=this.c
if(p!=null)q.push(new A.X("title",s,s,s,s,s,A.a([new A.k(p,s)],r),s))
r=this.e
if(r!=null)B.b.B(q,r)
return new A.fQ(B.c7,s,q,s)}}
A.ji.prototype={
E(){return"AttachTarget."+this.b}}
A.fQ.prototype={
aS(){var s=A.eI(t.Q),r=($.aQ+1)%16777215
$.aQ=r
return new A.lV(null,!1,!1,s,r,this,B.u)}}
A.lV.prototype={
dd(){var s=this.f
s.toString
return t.k7.a(s).d},
bo(){var s,r,q=this.f
q.toString
t.k7.a(q)
s=this.e
s.toString
s=new A.c7(A.a([],t.O),q.b,s)
s.cQ("")
r=A.ew(s.x)
B.b.m(r.f,s)
r.r=!0
s.seI(q.c)
return s},
b1(a){var s
t.df.a(a)
s=this.f
s.toString
t.k7.a(s)
a.sne(s.b)
a.seI(s.c)},
bp(){var s,r
this.iY()
s=this.d$
s.toString
t.df.a(s)
r=A.ew(s.x)
B.b.J(r.f,s)
r.cs()}}
A.c7.prototype={
sne(a){var s=this,r=s.x
if(r===a)return
r=A.ew(r)
B.b.J(r.f,s)
r.cs()
s.x=a
r=A.ew(a)
B.b.m(r.f,s)
r.r=!0
A.ew(s.x).cs()},
seI(a){return},
bH(a,b){var s,r,q,p,o=this
a.a=o
try{s=a.gae()
r=b==null?null:b.gae()
if(r==null&&B.b.v(o.w,s))return
if(r!=null&&!B.b.v(o.w,r))r=null
q=o.w
B.b.J(q,s)
p=r!=null?B.b.aU(q,r)+1:0
B.b.cg(q,p,s)
A.ew(o.x).cs()}finally{a.bc()}},
J(a,b){B.b.J(this.w,b.gae())
b.a=null
A.ew(this.x).cs()}}
A.jh.prototype={
geO(){var s,r=this,q=r.b
if(q===$){s=A.a7(A.p(v.G.document).querySelector(r.a.b))
s.toString
r.b!==$&&A.fI()
r.b=s
q=s}return q},
ghA(){var s,r=this,q=r.d
if(q===$){s=new A.nV(r).$0()
r.d!==$&&A.fI()
r.d=s
q=s}return q},
ghU(){return new A.d4(this.mJ(),t.kP)},
mJ(){var s=this
return function(){var r=0,q=1,p=[],o,n
return function $async$ghU(a,b,c){if(b===1){p.push(c)
r=q}for(;;)switch(r){case 0:o=s.ghA()
n=A.a7(o.a.nextSibling)
case 2:if(!(n!=null&&n!==o.b)){r=3
break}r=4
return a.b=n,1
case 4:n=A.a7(n.nextSibling)
r=2
break
case 3:return 0
case 1:return a.c=p.at(-1),3}}}},
gmD(){var s,r,q,p,o,n=this,m=n.e
if(m===$){s=A.t(t.N,t.m)
for(r=n.ghU(),q=r.$ti,r=new A.d5(r.a(),q.h("d5<1>")),q=q.c;r.p();){p=r.b
if(p==null)p=q.a(p)
o=n.ci(p)
if(typeof o=="string")s.i(0,o,p)}n.e!==$&&A.fI()
n.e=s
m=s}return m},
ci(a){var s,r,q,p,o,n=a instanceof $.yl()
if(!n)return null
A:{s=A.r(a.id)
n=s.length!==0
r=s
q=null
if(n){n=r
break A}p=A.r(a.tagName)
if("TITLE"!==p)n="BASE"===p
else n=!0
if(n){n="__"+A.r(a.tagName)
break A}if("META"===p){o=A.a7(A.p(a.attributes).getNamedItem("name"))
B:{if(t.m.b(o)){n="__meta:"+A.r(o.value)
break B}n=q
break B}break A}n=q
break A}return n},
nk(a){var s,r,q,p,o,n,m,l,k,j,i,h,g,f=this
if(a||f.r){B.b.ai(f.f,new A.nW())
f.r=!1}s=f.gmD()
r=t.m
q=A.ce(s,t.N,r)
p=A.x(new A.cI(s,A.n(s).h("cI<2>")),r)
for(s=f.f,r=s.length,o=0;o<s.length;s.length===r||(0,A.I)(s),++o)for(n=s[o].w,m=n.length,l=0;l<n.length;n.length===m||(0,A.I)(n),++l){k=n[l]
j=f.ci(k)
if(j!=null){i=q.j(0,j)
q.i(0,j,k)
if(i!=null){B.b.i(p,B.b.aU(p,i),k)
continue}}B.b.m(p,k)}s=f.ghA()
h=A.a7(s.a.nextSibling)
for(r=p.length,o=0;o<p.length;p.length===r||(0,A.I)(p),++o){k=p[o]
if(h==null||h===s.b)A.p(f.geO().insertBefore(k,h))
else if(h===k)h=A.a7(h.nextSibling)
else if(f.ci(k)!=null&&f.ci(k)==f.ci(h)){n=A.a7(h.parentNode)
if(n!=null)A.p(n.replaceChild(k,h))
h=A.a7(k.nextSibling)}else A.p(f.geO().insertBefore(k,h))}for(;;){if(!(h!=null&&h!==s.b))break
g=A.a7(h.nextSibling)
r=A.a7(h.parentNode)
if(r!=null)A.p(r.removeChild(h))
h=g}},
cs(){return this.nk(!1)}}
A.nV.prototype={
$0(){var s,r,q,p,o=v.G,n=A.p(o.document),m=this.a.geO(),l=A.p(n.createNodeIterator(m,128))
for(s=null,r=null;q=A.a7(l.nextNode()),q!=null;){p=A.aA(q.nodeValue)
if(p==null)p=""
if(p==="$")s=q
else if(p==="/")r=q}if(s==null){s=A.p(new o.Comment("$"))
A.p(m.insertBefore(s,r))}if(r==null){r=A.p(new o.Comment("/"))
A.p(m.insertBefore(r,A.a7(s.nextSibling)))}return new A.A(s,r)},
$S:80}
A.nW.prototype={
$2(a,b){var s=t.df
s.a(a)
s.a(b)
return a.z-b.z},
$S:79}
A.mX.prototype={
l(a){var s=this
return new A.X("aside",null,s.d,s.e,s.f,null,s.w,null)}}
A.n4.prototype={
l(a){var s=null
return new A.X("h1",s,this.d,this.e,s,s,this.w,s)}}
A.n5.prototype={
l(a){var s=null
return new A.X("h2",s,this.d,this.e,s,s,this.w,s)}}
A.n6.prototype={
l(a){var s=null
return new A.X("h3",s,this.d,this.e,s,s,this.w,s)}}
A.n7.prototype={
l(a){var s=null
return new A.X("h4",s,this.d,this.e,s,s,this.w,s)}}
A.n8.prototype={
l(a){var s=null
return new A.X("h5",s,this.d,this.e,s,s,this.w,s)}}
A.n9.prototype={
l(a){var s=null
return new A.X("h6",s,this.d,this.e,s,s,this.w,s)}}
A.ne.prototype={
l(a){var s=null
return new A.X("main",s,this.d,this.e,s,s,this.w,s)}}
A.ng.prototype={
l(a){var s=null
return new A.X("nav",s,this.d,this.e,s,s,this.w,s)}}
A.nl.prototype={
l(a){var s=null
return new A.X("section",s,this.d,s,s,s,this.w,s)}}
A.c.prototype={
l(a){var s=this
return new A.X("div",s.c,s.d,s.e,s.f,s.r,s.w,null)}}
A.nh.prototype={
l(a){var s=null
return new A.X("p",s,this.d,this.e,s,s,this.w,s)}}
A.nj.prototype={
l(a){var s=null
return new A.X("pre",s,this.d,this.e,s,s,this.w,s)}}
A.mZ.prototype={
l(a){var s,r=this,q=t.N,p=A.t(q,q)
p.B(0,r.y)
s=r.e==null?null:"button"
if(s!=null)p.i(0,"type",s)
q=A.t(q,t.v)
s=r.z
if(s!=null)q.B(0,s)
q.B(0,A.Cy().$1$1$onClick(null,t.H))
return new A.X("button",r.r,r.w,r.x,p,q,r.Q,null)}}
A.iU.prototype={
l(a){var s,r=this,q=null,p=t.N,o=A.t(p,p)
o.B(0,r.at)
o.i(0,"type",r.c.c)
s=A.C1(q)
if(s!=null)o.i(0,"checked",s)
s=A.C1(q)
if(s!=null)o.i(0,"indeterminate",s)
p=A.t(p,t.v)
p.B(0,r.ax)
p.B(0,A.Cy().$1$2$onChange$onInput(q,q,r.$ti.c))
return new A.X("input",r.z,r.Q,r.as,o,p,q,q)}}
A.cF.prototype={
E(){return"InputType."+this.b}}
A.nb.prototype={
l(a){var s=this,r=t.N
r=A.t(r,r)
r.B(0,s.r)
return new A.X("label",null,s.e,s.f,r,null,s.x,null)}}
A.nc.prototype={
l(a){var s=null,r=t.N
r=A.t(r,r)
r.i(0,"href","https://cdn.jsdelivr.net/npm/uplot@1.6.31/dist/uPlot.min.css")
r.i(0,"rel","stylesheet")
return new A.X("link",s,s,s,r,s,s,s)}}
A.iX.prototype={
l(a){var s,r,q=null,p=t.N
p=A.t(p,p)
s=this.c
if(s!=null)p.i(0,"src",s)
s=A.a([],t.i)
r=this.f
if(r!=null)s.push(new A.kG(r,q))
return new A.X("script",q,q,q,p,q,s,q)}}
A.no.prototype={
l(a){var s=this,r=null,q=t.N
q=A.t(q,q)
q.B(0,s.x)
q.i(0,"viewBox",s.c)
return new A.X("svg",r,r,s.w,q,r,s.z,r)}}
A.ni.prototype={
l(a){var s=null,r=t.N
r=A.t(r,r)
r.i(0,"points",this.c)
return new A.X("polyline",s,s,s,r,s,this.Q,s)}}
A.mY.prototype={
l(a){var s=null
return new A.X("br",s,s,s,s,s,s,s)}}
A.n0.prototype={
l(a){var s=null
return new A.X("code",s,this.d,this.e,s,s,this.w,s)}}
A.n2.prototype={
l(a){var s=null
return new A.X("em",s,this.d,this.e,s,s,this.w,s)}}
A.na.prototype={
l(a){var s=null
return new A.X("i",s,s,this.e,s,s,this.w,s)}}
A.nm.prototype={
l(a){var s=null
return new A.X("small",s,this.d,this.e,s,s,this.w,s)}}
A.ep.prototype={
l(a){var s=this
return new A.X("span",s.c,s.d,s.e,s.f,null,s.w,null)}}
A.nn.prototype={
l(a){var s=null
return new A.X("strong",s,this.d,this.e,s,s,this.w,s)}}
A.kG.prototype={
l(a){var s,r,q,p,o,n=A.p(A.p(v.G.document).createElement("template"))
n.innerHTML=this.c
s=A.a([],t.i)
for(r=A.qB(A.p(A.p(n.content).childNodes)),q=r.$ti,r=new A.d5(r.a(),q.h("d5<1>")),p=t.mg,q=q.c;r.p();){o=r.b
if(o==null)o=q.a(o)
s.push(new A.it(o,new A.e8(o,p)))}return new A.bK(s,null)}}
A.it.prototype={
aS(){var s=($.aQ+1)%16777215
$.aQ=s
return new A.mx(null,!1,!1,s,this,B.u)}}
A.mx.prototype={
gD(){return t.pj.a(A.C.prototype.gD.call(this))},
aH(a){this.iT(t.pj.a(a))},
bo(){var s,r=this.CW.d$
r.toString
s=new A.m5(t.pj.a(A.C.prototype.gD.call(this)).b)
s.a=r
return s},
b1(a){}}
A.m5.prototype={
bH(a,b){throw A.d(A.ao("Raw nodes cannot have children attached to them."))},
J(a,b){throw A.d(A.ao(u.x))},
bc(){},
dE(a){t.bD.a(a)
return null},
gae(){return this.d}}
A.ud.prototype={}
A.i7.prototype={
k(a){return"Color("+this.a+")"},
$iDD:1}
A.mT.prototype={}
A.lQ.prototype={$iEW:1}
A.fv.prototype={
N(a,b){var s,r,q,p=this
if(b==null)return!1
s=!0
if(p!==b){r=p.b
if(r===0)q=b instanceof A.fv&&b.b===0
else q=!1
if(!q)s=b instanceof A.fv&&A.bH(p)===A.bH(b)&&p.a===b.a&&r===b.b}return s},
gI(a){var s=this.b
return s===0?0:A.cL(this.a,s,B.d,B.d,B.d,B.d,B.d,B.d,B.d,B.d)},
$iyX:1}
A.m7.prototype={}
A.my.prototype={}
A.ln.prototype={}
A.lo.prototype={}
A.l.prototype={
gf5(){var s=this,r=null,q=t.N,p=A.t(q,q)
q=s.as==null?r:A.Gf(A.j(["",A.AE(2)+"em"],q,q),"padding")
if(q!=null)p.B(0,q)
q=s.mp
q=q==null?r:q.a
if(q!=null)p.i(0,"color",q)
q=s.mq
q=q==null?r:A.AE(q.b)+q.a
if(q!=null)p.i(0,"font-size",q)
q=s.mr
q=q==null?r:q.a
if(q!=null)p.i(0,"background-color",q)
q=s.ms
if(q!=null)p.B(0,q)
return p}}
A.xi.prototype={
$2(a,b){var s
A.r(a)
A.r(b)
s=a.length!==0?"-"+a:""
return new A.W(this.a+s,b,t.gc)},
$S:76}
A.mK.prototype={}
A.j1.prototype={}
A.lR.prototype={}
A.hN.prototype={
E(){return"SchedulerPhase."+this.b}}
A.kT.prototype={
im(a){var s=t.M
A.yi(s.a(new A.rx(this,s.a(a))))},
eL(){this.fP()},
fP(){var s,r=this.b$,q=A.x(r,t.M)
B.b.O(r)
for(r=q.length,s=0;s<q.length;q.length===r||(0,A.I)(q),++s)q[s].$0()}}
A.rx.prototype={
$0(){var s=this.a,r=t.M.a(this.b)
s.a$=B.jE
r.$0()
s.a$=B.jF
s.fP()
s.a$=B.bt
return null},
$S:0}
A.cm.prototype={
b_(a,b,c){var s=this.$ti.A(c).h("1/(2)").a(a).$1(this.a)
if(c.h("ae<0>").b(s))return s
return new A.cm(s,c.h("cm<0>"))},
ah(a,b){return this.b_(a,null,b)},
cu(a){var s,r,q,p,o,n,m=this
t.mY.a(a)
try{s=a.$0()
if(t.g7.b(s)){p=s.ah(new A.t3(m),m.$ti.c)
return p}return m}catch(o){r=A.a1(o)
q=A.b3(o)
p=A.zb(r,q)
n=new A.a_($.a0,m.$ti.h("a_<1>"))
n.c1(p)
return n}},
$iae:1}
A.t3.prototype={
$1(a){return this.a.a},
$S(){return this.a.$ti.h("1(@)")}}
A.jm.prototype={
io(a){var s=this
if(a.ax){s.e=!0
return}if(!s.b){a.r.im(s.gn0())
s.b=!0}B.b.m(s.a,a)
a.ax=!0},
du(a){return this.mL(t.mY.a(a))},
mL(a){var s=0,r=A.Q(t.H),q=1,p=[],o=[],n
var $async$du=A.R(function(b,c){if(b===1){p.push(c)
s=q}for(;;)switch(s){case 0:q=2
n=a.$0()
s=t.g7.b(n)?5:6
break
case 5:s=7
return A.G(n,$async$du)
case 7:case 6:o.push(4)
s=3
break
case 2:o=[1]
case 3:q=1
s=o.pop()
break
case 4:return A.O(null,r)
case 1:return A.N(p.at(-1),r)}})
return A.P($async$du,r)},
f3(a,b){return this.n2(a,t.M.a(b))},
n2(a,b){var s=0,r=A.Q(t.H),q=this
var $async$f3=A.R(function(c,d){if(c===1)return A.N(d,r)
for(;;)switch(s){case 0:q.c=!0
a.cJ(null,new A.de(null,0))
a.ap()
t.M.a(new A.o3(q,b)).$0()
return A.O(null,r)}})
return A.P($async$f3,r)},
n1(){var s,r,q,p,o,n,m,l,k,j,i,h=this
try{n=h.a
B.b.ai(n,A.zh())
h.e=!1
s=n.length
r=0
for(;;){m=r
l=s
if(typeof m!=="number")return m.il()
if(typeof l!=="number")return A.zk(l)
if(!(m<l))break
q=B.b.j(n,r)
try{q.cq()
q.toString}catch(k){p=A.a1(k)
n=A.w(p)
A.Ic("Error on rebuilding component: "+n)
throw k}m=r
if(typeof m!=="number")return m.fj()
r=m+1
m=s
l=n.length
if(typeof m!=="number")return m.il()
if(!(m<l)){m=h.e
m.toString}else m=!0
if(m){B.b.ai(n,A.zh())
m=h.e=!1
j=n.length
s=j
for(;;){l=r
if(typeof l!=="number")return l.al()
if(l>0){l=r
if(typeof l!=="number")return l.fo();--l
if(l>>>0!==l||l>=j)return A.f(n,l)
l=n[l].at}else l=m
if(!l)break
l=r
if(typeof l!=="number")return l.fo()
r=l-1}}}}finally{for(n=h.a,m=n.length,i=0;i<m;++i){o=n[i]
o.ax=!1}B.b.O(n)
h.e=null
h.du(h.d.glB())
h.b=!1}}}
A.o3.prototype={
$0(){this.a.c=!1
this.b.$0()},
$S:0}
A.fT.prototype={
ck(a,b){this.cJ(a,b)},
ap(){this.cq()
this.dT()},
bZ(a){return!0},
bQ(){var s,r,q,p,o,n,m=this,l=null,k=null
try{k=m.eK()}catch(q){s=A.a1(q)
r=A.b3(q)
k=new A.X("div",l,l,B.lQ,l,l,A.a([new A.k("Error on building component: "+A.w(s),l)],t.i),l)
m.r.i5(m,s,r)}finally{m.at=!1}p=m.cy
o=k
n=m.c
n.toString
m.cy=m.ct(p,o,n)},
mo(a,b){var s=this
s.r.i5(s,a,b)
s.at=!1
s.cy=null},
b2(a){var s
t.p9.a(a)
s=this.cy
if(s!=null)a.$1(s)}}
A.X.prototype={
aS(){var s=A.eI(t.Q),r=($.aQ+1)%16777215
$.aQ=r
return new A.jG(null,!1,!1,s,r,this,B.u)}}
A.jG.prototype={
gD(){return t.J.a(A.C.prototype.gD.call(this))},
dd(){var s=t.J.a(A.C.prototype.gD.call(this)).w
return s==null?A.a([],t.i):s},
d1(){var s,r,q,p,o=this
o.iL()
s=o.z
if(s!=null){r=s.K(B.bx)
q=s}else{q=null
r=!1}if(r){p=A.As(q,t.ha,t.a3)
o.ry=p.J(0,B.bx)
o.z=p
return}o.ry=null},
a5(){this.fq()
var s=this.d$
s.toString
this.b1(t.bY.a(s))},
aH(a){this.iX(t.J.a(a))},
cG(a){var s=this,r=t.J
r.a(a)
return r.a(A.C.prototype.gD.call(s)).c!=a.c||r.a(A.C.prototype.gD.call(s)).d!=a.d||r.a(A.C.prototype.gD.call(s)).e!=a.e||r.a(A.C.prototype.gD.call(s)).f!=a.f||r.a(A.C.prototype.gD.call(s)).r!=a.r},
bo(){var s,r,q=this.CW.d$
q.toString
s=t.J.a(A.C.prototype.gD.call(this))
r=new A.jH(A.a([],t.O))
r.a=q
r.cQ(s.b)
this.b1(r)
return r},
b1(a){var s,r,q,p,o,n,m,l=this
t.bY.a(a)
s=l.ry
if(s!=null){r=t.b_.a(l.mc(s))
s=t.J
q=s.a(A.C.prototype.gD.call(l)).c
if(q==null)q=r.gnv()
p=A.DM(r.gnt(),s.a(A.C.prototype.gD.call(l)).d)
o=r.gnr().gf5()
n=s.a(A.C.prototype.gD.call(l)).e
n=n==null?null:n.gf5()
m=t.N
a.ib(q,p,A.yz(o,n,m,m),A.yz(r.geI(),s.a(A.C.prototype.gD.call(l)).f,m,m),A.yz(r.gnu(),s.a(A.C.prototype.gD.call(l)).r,m,t.v))
return}s=t.J
q=s.a(A.C.prototype.gD.call(l))
p=s.a(A.C.prototype.gD.call(l))
o=s.a(A.C.prototype.gD.call(l)).e
o=o==null?null:o.gf5()
a.ib(q.c,p.d,o,s.a(A.C.prototype.gD.call(l)).f,s.a(A.C.prototype.gD.call(l)).r)}}
A.k.prototype={
aS(){var s=($.aQ+1)%16777215
$.aQ=s
return new A.lt(null,!1,!1,s,this,B.u)}}
A.lt.prototype={
gD(){return t.oI.a(A.C.prototype.gD.call(this))},
cG(a){var s=t.oI
s.a(a)
return s.a(A.C.prototype.gD.call(this)).b!==a.b},
bo(){var s=this.CW.d$
s.toString
return A.DN(t.oI.a(A.C.prototype.gD.call(this)).b,s)},
b1(a){var s,r
t.e8.a(a)
s=t.oI.a(A.C.prototype.gD.call(this)).b
r=a.d
r===$&&A.S()
if(A.aA(r.textContent)!==s)r.textContent=s}}
A.bK.prototype={
aS(){var s=A.eI(t.Q),r=($.aQ+1)%16777215
$.aQ=r
return new A.me(null,!1,!1,s,r,this,B.u)}}
A.me.prototype={
dd(){var s=this.f
s.toString
return t.gF.a(s).b},
bo(){var s,r,q=this.CW.d$
q.toString
s=t.O
r=new A.bV(A.p(A.p(v.G.document).createDocumentFragment()),A.a([],s))
r.a=q
q=t.fh.b(q)?q.k3$:A.a([],s)
r.k3$=q
return r},
b1(a){t.mj.a(a)}}
A.jx.prototype={
eH(a){var s=0,r=A.Q(t.H),q=this,p,o,n
var $async$eH=A.R(function(b,c){if(b===1)return A.N(c,r)
for(;;)switch(s){case 0:o=q.c$
n=o==null?null:o.w
if(n==null)n=new A.jm(A.a([],t.il),new A.mj(A.eI(t.Q)))
p=A.Fz(new A.iv(a,q.m8(),null))
p.r=q
p.w=n
q.c$=p
n.f3(p,q.gm4())
return A.O(null,r)}})
return A.P($async$eH,r)}}
A.iv.prototype={
aS(){var s=A.eI(t.Q),r=($.aQ+1)%16777215
$.aQ=r
return new A.iw(null,!1,!1,s,r,this,B.u)}}
A.iw.prototype={
dd(){var s=this.f
s.toString
return A.a([t.cf.a(s).b],t.i)},
bo(){var s=this.f
s.toString
return t.cf.a(s).c},
b1(a){}}
A.e.prototype={}
A.fm.prototype={
E(){return"_ElementLifecycle."+this.b}}
A.C.prototype={
N(a,b){if(b==null)return!1
return this===b},
gI(a){return this.d},
gD(){var s=this.f
s.toString
return s},
ct(a,b,c){var s,r,q,p=this
if(b==null){if(a!=null)p.hE(a)
return null}if(a!=null)if(a.f===b){s=a.c.N(0,c)
if(!s)p.ie(a,c)
r=a}else{s=A.oj(a.gD(),b)
if(s){s=a.c.N(0,c)
if(!s)p.ie(a,c)
q=a.gD()
a.aH(b)
a.bK(q)
r=a}else{p.hE(a)
r=p.hM(b,c)}}else r=p.hM(b,c)
return r},
nl(a4,a5,a6){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e,d,c,b,a,a0,a1,a2=this,a3=null
t.jB.a(a4)
t.kT.a(a5)
s=new A.oQ(t.an.a(a6))
r=new A.oR()
q=J.aT(a4)
if(q.gn(a4)<=1&&a5.length<=1){p=a2.ct(s.$1(A.yH(a4,t.Q)),A.yH(a5,t.aI),new A.de(a3,0))
q=A.a([],t.il)
if(p!=null)q.push(p)
return q}o=a5.length-1
n=q.gn(a4)-1
m=q.gn(a4)
l=a5.length
k=m===l?a4:A.bL(l,a3,!0,t.c_)
m=J.bl(k)
j=a3
i=0
h=0
for(;;){if(!(h<=n&&i<=o))break
g=s.$1(q.j(a4,h))
if(!(i<a5.length))return A.f(a5,i)
f=a5[i]
if(g==null||!A.oj(g.gD(),f))break
l=a2.ct(g,f,r.$2(i,j))
l.toString
m.i(k,i,l);++i;++h
j=l}for(;;){l=h<=n
if(!(l&&i<=o))break
g=s.$1(q.j(a4,n))
if(!(o>=0&&o<a5.length))return A.f(a5,o)
f=a5[o]
if(g==null||!A.oj(g.gD(),f))break;--n;--o}e=a3
if(i<=o&&l){l=t.er
d=A.t(l,t.aI)
for(c=i;c<=o;){if(!(c<a5.length))return A.f(a5,c)
f=a5[c]
b=f.a
if(b!=null)d.i(0,b,f);++c}if(d.a!==0){e=A.t(l,t.Q)
for(a=h;a<=n;){g=s.$1(q.j(a4,a))
if(g!=null){b=g.gD().a
if(b!=null){f=d.j(0,b)
if(f!=null&&A.oj(g.gD(),f))e.i(0,b,g)}}++a}}}for(l=e==null,a0=!l;i<=o;j=a1){if(h<=n){g=s.$1(q.j(a4,h))
if(g!=null){b=g.gD().a
if(b==null||!a0||!e.K(b)){g.a=null
g.c.a=null
a1=a2.w.d
if(g.x===B.L){g.bp()
g.bJ()
g.b2(A.y_())}a1.a.m(0,g)}}++h}if(!(i<a5.length))return A.f(a5,i)
f=a5[i]
b=f.a
if(b!=null)g=l?a3:e.j(0,b)
else g=a3
a1=a2.ct(g,f,r.$2(i,j))
a1.toString
m.i(k,i,a1);++i}while(h<=n){g=s.$1(q.j(a4,h))
if(g!=null){b=g.gD().a
if(b==null||!a0||!e.K(b)){g.a=null
g.c.a=null
l=a2.w.d
if(g.x===B.L){g.bp()
g.bJ()
g.b2(A.y_())}l.a.m(0,g)}}++h}o=a5.length-1
n=q.gn(a4)-1
for(;;){if(!(h<=n&&i<=o))break
g=q.j(a4,h)
if(!(i<a5.length))return A.f(a5,i)
l=a2.ct(g,a5[i],r.$2(i,j))
l.toString
m.i(k,i,l);++i;++h
j=l}return m.cc(k,t.Q)},
ck(a,b){var s,r,q=this
q.a=a
s=t.fX
if(s.b(a))r=a
else r=a==null?null:a.CW
q.CW=r
q.c=b
if(s.b(q))b.a=q
q.x=B.L
s=a!=null
if(s){r=a.e
r.toString;++r}else r=1
q.e=r
if(s){s=a.w
s.toString
q.w=s
s=a.r
s.toString
q.r=s}q.gD()
q.d1()
q.lD()
q.lT()},
ap(){},
aH(a){if(this.bZ(a))this.at=!0
this.f=a},
bK(a){if(this.at)this.cq()},
ie(a,b){new A.oS(b).$1(a)},
dI(a){this.c=a
if(t.fX.b(this))a.a=this},
hM(a,b){var s=a.aS()
s.ck(this,b)
s.ap()
return s},
hE(a){var s
a.a=null
a.c.a=null
s=this.w.d
if(a.x===B.L){a.bp()
a.bJ()
a.b2(A.y_())}s.a.m(0,a)},
bJ(){var s,r,q=this,p=q.Q
if(p!=null&&p.a!==0)for(s=A.n(p),p=new A.d3(p,p.eb(),s.h("d3<1>")),s=s.c;p.p();){r=p.d;(r==null?s.a(r):r).ry.J(0,q)}q.z=null
q.x=B.ki},
ff(){var s=this
s.gD()
s.Q=s.f=s.CW=null
s.x=B.kj},
hF(a,b){var s=this.Q;(s==null?this.Q=A.eI(t.a3):s).m(0,a)
a.ry.i(0,this,null)
return t.p.a(A.C.prototype.gD.call(a))},
mc(a){return this.hF(a,null)},
H(a){var s,r
A.Cr(a,t.p,"T","dependOnInheritedComponentOfExactType")
s=this.z
r=s==null?null:s.j(0,A.bc(a))
if(r!=null)return a.a(this.hF(r,null))
this.as=!0
return null},
d1(){var s=this.a
this.z=s==null?null:s.z},
lD(){var s=this.a
this.y=s==null?null:s.y},
lT(){var s=this.a
this.b=s==null?null:s.b},
a5(){this.hV()},
hV(){var s=this
if(s.x!==B.L)return
if(s.at)return
s.at=!0
s.w.io(s)},
cq(){var s=this
if(s.x!==B.L||!s.at)return
s.w.toString
s.bQ()
s.dh()},
dh(){var s,r,q=this.Q
if(q!=null&&q.a!==0)for(s=A.n(q),q=new A.d3(q,q.eb(),s.h("d3<1>")),s=s.c;q.p();){r=q.d
if(r==null)s.a(r)}},
bp(){this.b2(new A.oP())},
$iac:1}
A.oQ.prototype={
$1(a){return a!=null&&this.a.v(0,a)?null:a},
$S:74}
A.oR.prototype={
$2(a,b){return new A.de(b,a)},
$S:71}
A.oS.prototype={
$1(a){var s
a.dI(this.a)
if(!t.fX.b(a)){s={}
s.a=null
a.b2(new A.oT(s,this))}},
$S:15}
A.oT.prototype={
$1(a){this.a.a=a
this.b.$1(a)},
$S:15}
A.oP.prototype={
$1(a){a.bp()},
$S:15}
A.de.prototype={
N(a,b){if(b==null)return!1
if(J.yo(b)!==A.bH(this))return!1
return b instanceof A.de&&this.c===b.c&&J.a8(this.b,b.b)},
gI(a){return A.cL(this.c,this.b,B.d,B.d,B.d,B.d,B.d,B.d,B.d,B.d)}}
A.mj.prototype={
ht(a){a.b2(new A.vi(this))
a.ff()},
lC(){var s,r,q=this.a,p=A.x(q,A.n(q).c)
B.b.ai(p,A.zh())
q.O(0)
for(q=A.F(p).h("cN<1>"),s=new A.cN(p,q),s=new A.aw(s,s.gn(0),q.h("aw<z.E>")),q=q.h("z.E");s.p();){r=s.d
this.ht(r==null?q.a(r):r)}}}
A.vi.prototype={
$1(a){this.a.ht(a)},
$S:15}
A.aZ.prototype={
aS(){var s=A.yE(t.Q,t.X),r=($.aQ+1)%16777215
$.aQ=r
return new A.hj(s,r,this,B.u)}}
A.hj.prototype={
gD(){return t.p.a(A.C.prototype.gD.call(this))},
eK(){return t.p.a(A.C.prototype.gD.call(this)).b},
d1(){var s,r,q=this,p=q.a,o=p==null?null:p.z
p=t.ha
s=t.a3
r=o!=null?A.As(o,p,s):A.yE(p,s)
q.z=r
r.i(0,A.bH(t.p.a(A.C.prototype.gD.call(q))),q)},
bK(a){var s=t.p
s.a(a)
if(s.a(A.C.prototype.gD.call(this)).aO(a))this.mS(a)
this.cI(a)},
mS(a){var s,r,q
for(s=this.ry,r=A.n(s),s=new A.ec(s,s.ec(),r.h("ec<1>")),r=r.c;s.p();){q=s.d;(q==null?r.a(q):q).a5()}}}
A.eR.prototype={}
A.kf.prototype={}
A.e8.prototype={
N(a,b){if(b==null)return!1
return J.yo(b)===A.bH(this)&&this.$ti.b(b)&&b.a===this.a},
gI(a){return A.AF([A.bH(this),this.a])},
k(a){var s=this.$ti,r=s.c,q=this.a,p=A.bc(r)===B.k8?"<'"+A.w(q)+"'>":"<"+A.w(q)+">"
if(A.bH(this)===A.bc(s))return"["+p+"]"
return"["+A.bc(r).k(0)+" "+p+"]"}}
A.hu.prototype={
ck(a,b){this.cJ(a,b)},
ap(){this.cq()
this.dT()},
bZ(a){return!1},
bQ(){this.at=!1},
b2(a){t.p9.a(a)}}
A.hx.prototype={
ck(a,b){this.cJ(a,b)},
ap(){this.cq()
this.dT()},
bZ(a){return!0},
bQ(){var s,r,q,p=this
p.at=!1
s=p.dd()
r=p.cy
if(r==null)r=A.a([],t.il)
q=p.db
p.cy=p.nl(r,s,q)
q.O(0)},
b2(a){var s,r,q,p
t.p9.a(a)
s=this.cy
if(s!=null)for(r=J.aE(s),q=this.db;r.p();){p=r.gu()
if(!q.v(0,p))a.$1(p)}}}
A.eX.prototype={
ap(){var s=this
if(s.d$==null)s.d$=s.bo()
s.iW()},
dh(){this.fs()
if(!this.f$)this.dc()},
aH(a){if(this.cG(a))this.e$=!0
this.dU(a)},
bK(a){var s,r=this
if(r.e$){r.e$=!1
s=r.d$
s.toString
r.b1(s)}r.cI(a)},
dI(a){this.ft(a)
this.dc()}}
A.eS.prototype={
ap(){var s=this
if(s.d$==null)s.d$=s.bo()
s.iS()},
dh(){this.fs()
if(!this.f$)this.dc()},
aH(a){if(this.cG(a))this.e$=!0
this.dU(a)},
bK(a){var s,r=this
if(r.e$){r.e$=!1
s=r.d$
s.toString
r.b1(s)}r.cI(a)},
dI(a){this.ft(a)
this.dc()}}
A.bp.prototype={
cG(a){return!0},
dc(){var s,r,q,p=this,o=p.CW
if(o==null)s=null
else{o=o.d$
o.toString
s=o}if(s!=null){o=p.c.b
r=o==null?null:o.c.a
o=p.d$
o.toString
if(r==null)q=null
else{q=r.d$
q.toString}s.bH(o,q)}p.f$=!0},
bp(){var s,r=this.CW
if(r==null)s=null
else{r=r.d$
r.toString
s=r}if(s!=null){r=this.d$
r.toString
s.J(0,r)}this.f$=!1}}
A.af.prototype={
aS(){var s=this.U(),r=($.aQ+1)%16777215
$.aQ=r
r=new A.hT(s,r,this,B.u)
s.c=r
s.sfK(this)
return r}}
A.M.prototype={
aW(){},
bq(a){A.n(this).h("M.T").a(a)},
t(a){t.M.a(a).$0()
this.c.hV()},
aq(){},
a5(){},
sfK(a){this.a=A.n(this).h("M.T?").a(a)}}
A.dU.prototype={}
A.hT.prototype={
eK(){return this.ry.l(this)},
ap(){var s,r=this
if(r.w.c){s=r.ry
s.toString
if(t.eg.b(s))r.r.toString}r.kf()
r.fp()},
kf(){try{this.ry.aW()}finally{}this.ry.a5()},
bQ(){var s,r=this
if(r.w.c&&r.to!=null){s=t.a
return A.DX(r.to.ah(new A.rW(r),s),new A.rX(r),s,t.K)}if(r.x1){r.ry.a5()
r.x1=!1}r.dS()},
bZ(a){var s
t.mi.a(a)
s=this.ry
s.toString
A.n(s).h("M.T").a(a)
return!0},
aH(a){t.mi.a(a)
this.dU(a)
this.ry.sfK(a)},
bK(a){t.mi.a(a)
try{this.ry.bq(a)}finally{}this.cI(a)},
bJ(){this.ry.toString
this.iM()},
ff(){var s=this
s.iN()
s.ry.aq()
s.ry=s.ry.c=null},
a5(){this.fq()
this.x1=!0}}
A.rW.prototype={
$1(a){var s=this.a
if(s.x1){s.ry.a5()
s.x1=!1}s.dS()},
$S:49}
A.rX.prototype={
$2(a,b){this.a.mo(a,b)},
$S:13}
A.o.prototype={
aS(){var s=($.aQ+1)%16777215
$.aQ=s
return new A.lh(s,this,B.u)}}
A.lh.prototype={
gD(){return t.ft.a(A.C.prototype.gD.call(this))},
ap(){if(this.w.c)this.r.toString
this.fp()},
bZ(a){t.ft.a(A.C.prototype.gD.call(this))
return!0},
eK(){return t.ft.a(A.C.prototype.gD.call(this)).l(this)},
bQ(){this.w.toString
this.dS()}}
A.rg.prototype={
l(a){var s=a.d,r=s==null
if((r?$.zt():s).a.length===0)return new A.k("",null)
if(r)s=$.zt()
return new A.hk(a,this.jm(s,a.e),null)},
jm(a,b){var s,r,q
t.ln.a(b)
try{r=this.e1(a,0,b)
return r}catch(q){r=A.a1(q)
if(r instanceof A.ix){s=r
return this.jk(s,a.d)}else throw q}},
e1(a,b,c){var s,r,q,p,o,n,m,l,k,j=this
t.ln.a(c)
s=a.a
if(!(b<s.length))return A.f(s,b)
r=s[b]
q=r.d
if(q!=null)throw A.d(A.FA("Match error found during build phase",q))
p=r.a
o=p instanceof A.cP
n=o?p.b:""
m=a.d
l=t.N
k=new A.aL(m.k(0),r.b,null,n,a.b,A.qe(a.c,l,l),m.gdA(),m.gdB(),r.c,q)
if(o){q=b+1
if(s.length>q)return j.e1(a,q,c)
return j.jq(k,p,c)}else if(p instanceof A.dq)return j.jr(k,p,c,j.e1(a,b+1,c))
throw A.d(new A.mD("Unsupported route type "+p.k(0)))},
jq(a,b,c){t.ln.a(c)
return new A.eN(a,new A.fU(new A.rh(b.e,a),null),null)},
jr(a,b,c,d){t.ln.a(c)
return new A.eN(a,new A.fU(new A.ri(b.b,a,d),null),null)},
jk(a,b){b.k(0)
b.gab()
b.gdA()
b.gdB()
return new A.jM(new A.dw(a),null)}}
A.rh.prototype={
$1(a){return this.a.$2(t.gC.a(a),this.b)},
$S:50}
A.ri.prototype={
$1(a){return this.a.$3(t.gC.a(a),this.b,this.c)},
$S:50}
A.ix.prototype={
k(a){var s=this.b
return this.a+" "+A.w(s==null?"":s)}}
A.mD.prototype={
k(a){return this.a+" "},
$iaj:1}
A.f7.prototype={
k(a){return"RouterConfiguration: "+A.w(this.a)},
e2(a,b){var s,r,q,p,o
t.hb.a(b)
for(s=b.length,r=0;r<b.length;b.length===s||(0,A.I)(b),++r){q=b[r]
if(q instanceof A.cP){p=A.Cs(a,q.b)
o=q.a
if(o.length!==0)this.e2(p,o)}else if(q instanceof A.dq){o=q.a
if(o.length!==0)this.e2(a,o)}}}}
A.cQ.prototype={}
A.f8.prototype={
hI(a,b){var s,r=A.bN(A.Cq(a)),q=t.N,p=A.t(q,q)
t.f.a(p)
s=A.C3(b,r.gab(),"",p,r.gab(),this.a.a)
if(s==null)A.ak(A.AB("no routes for location",r.k(0)))
return new A.aq(s,A.rn(s),p,r)},
mu(a){return this.hI(a,null)}}
A.aq.prototype={
gdF(){var s=this.a
return new A.cN(s,A.F(s).h("cN<1>")).eQ(0,null,new A.ro(),t.jv)},
gmE(){var s=this.a
return s.length===1&&B.b.gaz(s).d!=null},
k(a){return"RouteMatchList("+this.b+")"}}
A.ro.prototype={
$2(a,b){var s
A.aA(a)
t.dv.a(b)
if(a==null)s=null
else s=a
return s},
$S:70}
A.eV.prototype={
k(a){return this.a}}
A.xX.prototype={
$2(a,b){throw A.d(A.yW(null))},
$S:78}
A.jM.prototype={
l(a){var s=null,r=this.c
r=r==null?s:r.k(0)
if(r==null)r="page not found"
return new A.c(s,s,s,s,s,A.a([new A.k("Page Not Found",s),new A.mY(s),new A.k(r,s)],t.i),s)}}
A.hk.prototype={
aO(a){t.hj.a(a)
return!0}}
A.eN.prototype={
aO(a){return!this.d.N(0,t.hn.a(a).d)}}
A.rj.prototype={
mY(a,b,c){var s,r,q,p,o=A.Bk()
try{o.shH(this.b.hI(a,c))}catch(s){if(A.a1(s) instanceof A.eV){r=A.a([],t.E)
q=A.bN(A.Cq(a))
o.shH(new A.aq(r,A.rn(r),B.x,q))}else throw s}r=new A.rk(a)
p=A.Id().$5$extra(b,o.hc(),this.a,this.b,c)
if(p instanceof A.aq)return r.$1(p)
return p.ah(r,t.b)}}
A.rk.prototype={
$1(a){var s
t.b.a(a)
if(a.a.length===0){s=this.a
return new A.cm(A.Cw(A.bN(s),"no routes for location: "+s),t.b7)}return new A.cm(a,t.b7)},
$S:53}
A.xh.prototype={
$1(a){var s=a.b
if(0>=s.length)return A.f(s,0)
return"\\"+A.w(s[0])},
$S:22}
A.qJ.prototype={}
A.jZ.prototype={
mC(a,b){t.aD.a(b)
A.yZ(A.p(v.G.window),"popstate",t.bl.a(new A.pR(b)),!1,t.m)},
i3(a,b,c){var s=A.p(A.p(v.G.window).history),r=A.zo(b),q=c==null?a:c
s.replaceState(r,q,a)},
n9(a,b){return this.i3(a,null,b)},
$iE4:1}
A.pR.prototype={
$1(a){this.a.$1(A.p(A.p(v.G.window).history).state)},
$S:4}
A.kQ.prototype={$iEO:1}
A.yg.prototype={
$1(a){var s,r,q,p,o,n=this
A.aA(a)
if(a!=null&&a!==n.b){s=n.d
r=n.e
q=n.a
p=q.a
p.toString
o=A.Gl(a,n.c.d,s,r,p)
if(o.gmE())return o
return A.yf(n.f,o,s,r,n.r,q.a)}s=n.c
r=n.d
q=n.f
s=new A.yh(n.a,n.b,s,r,n.e,q,n.r).$1(A.C4(q,r,s,0))
return s},
$S:54}
A.yh.prototype={
$1(a){this.f.r.toString
return this.c},
$S:54}
A.xj.prototype={
$1(a){var s=this,r=A.C4(s.a,s.b,s.c,s.d+1)
return r},
$S:69}
A.dY.prototype={}
A.cP.prototype={}
A.dq.prototype={}
A.dn.prototype={
j2(a,b,c,d,e){var s=this,r=s.c,q=t.N
q=new A.f7(r,5,new A.rv(),A.t(q,q))
q.e2("",r)
s.r!==$&&A.bT()
s.r=q
s.w!==$&&A.bT()
s.w=new A.rj(q,new A.f8(q))
s.x!==$&&A.bT()
s.x=new A.rg(null)},
U(){return new A.dZ(A.t(t.K,t.oN))}}
A.rv.prototype={
$2(a,b){t.gC.a(a)
t.aT.a(b)
return null},
$S:68}
A.dZ.prototype={
aW(){var s,r,q=this
q.bi()
s=$.np()
r=q.c
r.toString
s.a.mC(r,new A.ru(q))
if(q.d==null)q.hN()},
bq(a){var s
t.nA.a(a)
this.c0(a)
s=this.a
s.toString
if(s===a)return
this.hN()},
hN(){var s=this,r=s.c.r.ghD()
return s.kn(r).ah(s.gkF(),t.b).ah(new A.rt(s,r),t.H)},
hu(a,b,c,d){return this.fW(a,b).ah(new A.rr(this,d,a,c),t.H)},
b9(a,b){return this.hu(a,b,!1,!0)},
kG(a){var s,r,q,p=t.b
p.a(a)
s=A.a([],t.ai)
for(r=a.a.length,q=0;q<r;++q);return A.EL(s).ah(new A.rp(a),p)},
fW(a,b){var s,r=this.a.w
r===$&&A.S()
s=this.c
s.toString
return r.mY(a,s,b)},
kn(a){return this.fW(a,null)},
l(a){var s=null,r=A.a([],t.i),q=this.d,p=q==null?s:q.gdF()
if(p!=null)r.push(new A.hh(p,s,s))
q=this.a.x
q===$&&A.S()
r.push(q.l(this))
return new A.bK(r,s)}}
A.ru.prototype={
$2$url(a,b){var s=this.a,r=s.c.r.ghD()
s.hu(r,a,!0,!1)},
$1(a){return this.$2$url(a,null)},
$S:63}
A.rt.prototype={
$1(a){var s,r
t.b.a(a)
s=this.a
r=s.c
if(r==null)return
s.d=a
r.r.toString
s.t(new A.rs())
s.c.r.toString
s=a.d
r=s.k(0)
if(r!==this.b)$.np().a.n9(s.k(0),a.gdF())},
$S:42}
A.rs.prototype={
$0(){},
$S:0}
A.rr.prototype={
$1(a){var s,r=this
t.b.a(a)
s=r.a
if(s.c==null)return
s.t(new A.rq(s,a,r.b,r.c,r.d))},
$S:42}
A.rq.prototype={
$0(){var s,r,q,p=this,o=p.a.d=p.b
if(p.c||p.d!==o.d.k(0)){s=o.d
if(!p.e){$.np()
s=s.k(0)
r=o.gdF()
o=o.a
o=o.length===0?null:B.b.gaL(o).c
q=A.p(A.p(v.G.window).history)
o=A.zo(o)
if(r==null)r=s
q.pushState(o,r,s)}else{r=$.np()
s=s.k(0)
q=o.gdF()
o=o.a
o=o.length===0?null:B.b.gaL(o).c
r.a.i3(s,o,q)}}},
$S:0}
A.rp.prototype={
$1(a){return this.a},
$S:59}
A.rm.prototype={
$1(a){return t.oN.a(a).b},
$S:60}
A.mE.prototype={}
A.aL.prototype={
N(a,b){var s=this
if(b==null)return!1
return b instanceof A.aL&&b.a===s.a&&b.b===s.b&&b.d==s.d&&b.e==s.e&&b.f===s.f&&b.r===s.r&&b.w===s.w&&J.a8(b.x,s.x)&&b.y==s.y},
gI(a){var s=this
return A.cL(s.a,s.b,s.c,s.d,s.e,s.f,s.r,s.w,s.x,s.y)}}
A.ov.prototype={
lN(a){var s,r,q=t.mf
A.Cm("absolute",A.a([a,null,null,null,null,null,null,null,null,null,null,null,null,null,null],q))
s=this.a
s=s.ak(a)>0&&!s.bd(a)
if(s)return a
s=A.Cu()
r=A.a([s,a,null,null,null,null,null,null,null,null,null,null,null,null,null,null],q)
A.Cm("join",r)
return this.mG(new A.hZ(r,t.lS))},
mG(a){var s,r,q,p,o,n,m,l,k,j
t.bq.a(a)
for(s=a.$ti,r=s.h("y(m.E)").a(new A.ow()),q=a.gC(0),s=new A.e9(q,r,s.h("e9<m.E>")),r=this.a,p=!1,o=!1,n="";s.p();){m=q.gu()
if(r.bd(m)&&o){l=A.kz(m,r)
k=n.charCodeAt(0)==0?n:n
n=B.a.q(k,0,r.bS(k,!0))
l.b=n
if(r.cl(n))B.b.i(l.e,0,r.gbw())
n=l.k(0)}else if(r.ak(m)>0){o=!r.bd(m)
n=m}else{j=m.length
if(j!==0){if(0>=j)return A.f(m,0)
j=r.eM(m[0])}else j=!1
if(!j)if(p)n+=r.gbw()
n+=m}p=r.cl(m)}return n.charCodeAt(0)==0?n:n},
fm(a,b){var s=A.kz(b,this.a),r=s.d,q=A.F(r),p=q.h("a3<1>")
r=A.x(new A.a3(r,q.h("y(1)").a(new A.ox()),p),p.h("m.E"))
s.smZ(r)
r=s.b
if(r!=null)B.b.cg(s.d,0,r)
return s.d},
cn(a){var s
if(!this.kr(a))return a
s=A.kz(a,this.a)
s.f_()
return s.k(0)},
kr(a){var s,r,q,p,o,n,m,l=this.a,k=l.ak(a)
if(k!==0){if(l===$.nq())for(s=a.length,r=0;r<k;++r){if(!(r<s))return A.f(a,r)
if(a.charCodeAt(r)===47)return!0}q=k
p=47}else{q=0
p=null}for(s=a.length,r=q,o=null;r<s;++r,o=p,p=n){if(!(r>=0))return A.f(a,r)
n=a.charCodeAt(r)
if(l.aX(n)){if(l===$.nq()&&n===47)return!0
if(p!=null&&l.aX(p))return!0
if(p===46)m=o==null||o===46||l.aX(o)
else m=!1
if(m)return!0}}if(p==null)return!0
if(l.aX(p))return!0
if(p===46)l=o==null||l.aX(o)||o===46
else l=!1
if(l)return!0
return!1},
n4(a){var s,r,q,p,o,n,m,l=this,k='Unable to find a path to "',j=l.a,i=j.ak(a)
if(i<=0)return l.cn(a)
s=A.Cu()
if(j.ak(s)<=0&&j.ak(a)>0)return l.cn(a)
if(j.ak(a)<=0||j.bd(a))a=l.lN(a)
if(j.ak(a)<=0&&j.ak(s)>0)throw A.d(A.AK(k+a+'" from "'+s+'".'))
r=A.kz(s,j)
r.f_()
q=A.kz(a,j)
q.f_()
i=r.d
p=i.length
if(p!==0){if(0>=p)return A.f(i,0)
i=i[0]==="."}else i=!1
if(i)return q.k(0)
i=r.b
p=q.b
if(i!=p)i=i==null||p==null||!j.f1(i,p)
else i=!1
if(i)return q.k(0)
for(;;){i=r.d
p=i.length
o=!1
if(p!==0){n=q.d
m=n.length
if(m!==0){if(0>=p)return A.f(i,0)
i=i[0]
if(0>=m)return A.f(n,0)
n=j.f1(i,n[0])
i=n}else i=o}else i=o
if(!i)break
B.b.bR(r.d,0)
B.b.bR(r.e,1)
B.b.bR(q.d,0)
B.b.bR(q.e,1)}i=r.d
p=i.length
if(p!==0){if(0>=p)return A.f(i,0)
i=i[0]===".."}else i=!1
if(i)throw A.d(A.AK(k+a+'" from "'+s+'".'))
i=t.N
B.b.eV(q.d,0,A.bL(p,"..",!1,i))
B.b.i(q.e,0,"")
B.b.eV(q.e,1,A.bL(r.d.length,j.gbw(),!1,i))
j=q.d
i=j.length
if(i===0)return"."
if(i>1&&B.b.gaL(j)==="."){B.b.i_(q.d)
j=q.e
if(0>=j.length)return A.f(j,-1)
j.pop()
if(0>=j.length)return A.f(j,-1)
j.pop()
B.b.m(j,"")}q.b=""
q.i0()
return q.k(0)},
hY(a){var s,r,q=this,p=A.Ca(a)
if(p.gam()==="file"&&q.a===$.iZ())return p.k(0)
else if(p.gam()!=="file"&&p.gam()!==""&&q.a!==$.iZ())return p.k(0)
s=q.cn(q.a.f0(A.Ca(p)))
r=q.n4(s)
return q.fm(0,r).length>q.fm(0,s).length?s:r}}
A.ow.prototype={
$1(a){return A.r(a)!==""},
$S:5}
A.ox.prototype={
$1(a){return A.r(a).length!==0},
$S:5}
A.xq.prototype={
$1(a){A.aA(a)
return a==null?"null":'"'+a+'"'},
$S:62}
A.eP.prototype={
ik(a){var s,r=this.ak(a)
if(r>0)return B.a.q(a,0,r)
if(this.bd(a)){if(0>=a.length)return A.f(a,0)
s=a[0]}else s=null
return s},
f1(a,b){return a===b}}
A.qH.prototype={
i0(){var s,r,q=this
for(;;){s=q.d
if(!(s.length!==0&&B.b.gaL(s)===""))break
B.b.i_(q.d)
s=q.e
if(0>=s.length)return A.f(s,-1)
s.pop()}s=q.e
r=s.length
if(r!==0)B.b.i(s,r-1,"")},
f_(){var s,r,q,p,o,n,m=this,l=A.a([],t.s)
for(s=m.d,r=s.length,q=0,p=0;p<s.length;s.length===r||(0,A.I)(s),++p){o=s[p]
if(!(o==="."||o===""))if(o===".."){n=l.length
if(n!==0){if(0>=n)return A.f(l,-1)
l.pop()}else ++q}else B.b.m(l,o)}if(m.b==null)B.b.eV(l,0,A.bL(q,"..",!1,t.N))
if(l.length===0&&m.b==null)B.b.m(l,".")
m.d=l
s=m.a
m.e=A.bL(l.length+1,s.gbw(),!0,t.N)
r=m.b
if(r==null||l.length===0||!s.cl(r))B.b.i(m.e,0,"")
r=m.b
if(r!=null&&s===$.nq())m.b=A.d8(r,"/","\\")
m.i0()},
k(a){var s,r,q,p,o,n=this.b
n=n!=null?n:""
for(s=this.d,r=s.length,q=this.e,p=q.length,o=0;o<r;++o){if(!(o<p))return A.f(q,o)
n=n+q[o]+s[o]}n+=B.b.gaL(q)
return n.charCodeAt(0)==0?n:n},
smZ(a){this.d=t.h.a(a)}}
A.kA.prototype={
k(a){return"PathException: "+this.a},
$iaj:1}
A.t2.prototype={
k(a){return this.gbe()}}
A.kD.prototype={
eM(a){return B.a.v(a,"/")},
aX(a){return a===47},
cl(a){var s,r=a.length
if(r!==0){s=r-1
if(!(s>=0))return A.f(a,s)
s=a.charCodeAt(s)!==47
r=s}else r=!1
return r},
bS(a,b){var s=a.length
if(s!==0){if(0>=s)return A.f(a,0)
s=a.charCodeAt(0)===47}else s=!1
if(s)return 1
return 0},
ak(a){return this.bS(a,!1)},
bd(a){return!1},
f0(a){var s
if(a.gam()===""||a.gam()==="file"){s=a.gab()
return A.d6(s,0,s.length,B.l,!1)}throw A.d(A.ai("Uri "+a.k(0)+" must have scheme 'file:'.",null))},
gbe(){return"posix"},
gbw(){return"/"}}
A.lE.prototype={
eM(a){return B.a.v(a,"/")},
aX(a){return a===47},
cl(a){var s,r=a.length
if(r===0)return!1
s=r-1
if(!(s>=0))return A.f(a,s)
if(a.charCodeAt(s)!==47)return!0
return B.a.a8(a,"://")&&this.ak(a)===r},
bS(a,b){var s,r,q,p=a.length
if(p===0)return 0
if(0>=p)return A.f(a,0)
if(a.charCodeAt(0)===47)return 1
for(s=0;s<p;++s){r=a.charCodeAt(s)
if(r===47)return 0
if(r===58){if(s===0)return 0
q=B.a.aV(a,"/",B.a.V(a,"//",s+1)?s+3:s)
if(q<=0)return p
if(!b||p<q+3)return q
if(!B.a.M(a,"file://"))return q
p=A.Cv(a,q+1)
return p==null?q:p}}return 0},
ak(a){return this.bS(a,!1)},
bd(a){var s=a.length
if(s!==0){if(0>=s)return A.f(a,0)
s=a.charCodeAt(0)===47}else s=!1
return s},
f0(a){return a.k(0)},
gbe(){return"url"},
gbw(){return"/"}}
A.lH.prototype={
eM(a){return B.a.v(a,"/")},
aX(a){return a===47||a===92},
cl(a){var s,r=a.length
if(r===0)return!1
s=r-1
if(!(s>=0))return A.f(a,s)
s=a.charCodeAt(s)
return!(s===47||s===92)},
bS(a,b){var s,r,q=a.length
if(q===0)return 0
if(0>=q)return A.f(a,0)
if(a.charCodeAt(0)===47)return 1
if(a.charCodeAt(0)===92){if(q>=2){if(1>=q)return A.f(a,1)
s=a.charCodeAt(1)!==92}else s=!0
if(s)return 1
r=B.a.aV(a,"\\",2)
if(r>0){r=B.a.aV(a,"\\",r+1)
if(r>0)return r}return q}if(q<3)return 0
if(!A.CF(a.charCodeAt(0)))return 0
if(a.charCodeAt(1)!==58)return 0
q=a.charCodeAt(2)
if(!(q===47||q===92))return 0
return 3},
ak(a){return this.bS(a,!1)},
bd(a){return this.ak(a)===1},
f0(a){var s,r
if(a.gam()!==""&&a.gam()!=="file")throw A.d(A.ai("Uri "+a.k(0)+" must have scheme 'file:'.",null))
s=a.gab()
if(a.gbr()===""){if(s.length>=3&&B.a.M(s,"/")&&A.Cv(s,1)!=null)s=B.a.dD(s,"/","")}else s="\\\\"+a.gbr()+s
r=A.d8(s,"/","\\")
return A.d6(r,0,r.length,B.l,!1)},
m1(a,b){var s
if(a===b)return!0
if(a===47)return b===92
if(a===92)return b===47
if((a^b)!==32)return!1
s=a|32
return s>=97&&s<=122},
f1(a,b){var s,r,q
if(a===b)return!0
s=a.length
r=b.length
if(s!==r)return!1
for(q=0;q<s;++q){if(!(q<r))return A.f(b,q)
if(!this.m1(a.charCodeAt(q),b.charCodeAt(q)))return!1}return!0},
gbe(){return"windows"},
gbw(){return"\\"}}
A.c0.prototype={}
A.xt.prototype={
$2(a,b){return A.G5(a)},
$S:3}
A.xu.prototype={
$2(a,b){return B.jK},
$S:64}
A.xv.prototype={
$2(a,b){return A.G4(a)},
$S:3}
A.xG.prototype={
$2(a,b){return B.bM},
$S:65}
A.xM.prototype={
$2(a,b){return B.cw},
$S:66}
A.xN.prototype={
$2(a,b){return A.aO(a,b,B.iH)},
$S:3}
A.xO.prototype={
$2(a,b){return A.aO(a,b,B.iI)},
$S:3}
A.xP.prototype={
$2(a,b){return A.aO(a,b,B.h1)},
$S:3}
A.xQ.prototype={
$2(a,b){return A.aO(a,b,B.cD)},
$S:3}
A.xR.prototype={
$2(a,b){return A.aO(a,b,B.cv)},
$S:3}
A.xS.prototype={
$2(a,b){return A.aO(a,b,B.h0)},
$S:3}
A.xw.prototype={
$2(a,b){return A.aO(a,b,B.cF)},
$S:3}
A.xx.prototype={
$2(a,b){return A.aO(a,b,B.d5)},
$S:3}
A.xy.prototype={
$2(a,b){return A.aO(a,b,B.cW)},
$S:3}
A.xz.prototype={
$2(a,b){return A.aO(a,b,B.kf)},
$S:3}
A.xA.prototype={
$2(a,b){return A.aO(a,b,B.d3)},
$S:3}
A.xB.prototype={
$2(a,b){return A.aO(a,b,B.cU)},
$S:3}
A.xC.prototype={
$2(a,b){return A.aO(a,b,B.iG)},
$S:3}
A.xD.prototype={
$2(a,b){return A.aO(a,b,B.jZ)},
$S:3}
A.xE.prototype={
$2(a,b){return A.aO(a,b,B.cT)},
$S:3}
A.xF.prototype={
$2(a,b){return A.aO(a,b,B.ke)},
$S:3}
A.xH.prototype={
$2(a,b){return A.aO(a,b,B.bL)},
$S:3}
A.xI.prototype={
$2(a,b){return A.aO(a,b,B.cV)},
$S:3}
A.xJ.prototype={
$2(a,b){return A.aO(a,b,B.cE)},
$S:3}
A.xK.prototype={
$2(a,b){return A.aO(a,b,B.cx)},
$S:3}
A.xL.prototype={
$2(a,b){return A.aO(a,b,B.dA)},
$S:3}
A.xT.prototype={
$3(a,b,c){return new A.f4(this.a,this.b,c,b.a,null)},
$S:67}
A.kH.prototype={
l(a){var s=null,r=t.i,q=A.a([B.md,B.me,new A.X("style",s,s,s,s,s,B.dk,s)],r)
return new A.eu(B.cp,B.aB,new A.bK(A.a([new A.f3(this.d,s),B.bW],r),s),"Reactor","React plugin monitoring dashboard",q,s)}}
A.dS.prototype={
U(){return new A.mr()}}
A.mr.prototype={
aW(){this.bi()
this.hb(this.a.d)},
bq(a){var s=this
t.jb.a(a)
s.c0(a)
if(a.d!==s.a.d){s.hr()
s.hb(s.a.d)}},
hb(a){var s,r=this
r.e=a.f
r.d=null
s=a.at
r.f=new A.aM(s,A.n(s).h("aM<1>")).bP(new A.vu(r))
s=a.ax
r.r=new A.aM(s,A.n(s).h("aM<1>")).bP(new A.vv(r))},
hr(){var s=this,r=s.f
if(r!=null)r.W()
r=s.r
if(r!=null)r.W()
s.r=s.f=null},
aq(){this.hr()
this.by()},
l(a){var s=this.d,r=this.e
r===$&&A.S()
return new A.hO(s,r,this.a.e,null)}}
A.vu.prototype={
$1(a){var s
t.c.a(a)
s=this.a
if(s.c==null)return
s.t(new A.vt(s,a))},
$S:19}
A.vt.prototype={
$0(){return this.a.d=this.b},
$S:0}
A.vv.prototype={
$1(a){var s
t.x.a(a)
s=this.a
if(s.c==null)return
s.t(new A.vs(s,a))},
$S:16}
A.vs.prototype={
$0(){return this.a.e=this.b},
$S:0}
A.mt.prototype={}
A.is.prototype={
l(a){var s=this,r=null,q=s.f?"reactor-nav-item active":"reactor-nav-item",p=A.j(["click",new A.vF(s)],t.N,t.v),o=t.i
return A.fE(A.a([A.H(A.a([s.e.$1$size(B.a_)],o),r,"reactor-nav-ico",r,r),A.H(A.a([new A.k(s.d,r)],o),r,r,r,r)],o),B.ae,q,p,r,r,r)}}
A.vF.prototype={
$1(a){A.p(a)
return this.a.r.$0()},
$S:4}
A.f4.prototype={
gke(){return B.b.bG(this.d,new A.r7(this))},
gfU(){var s=this.d,r=A.F(s)
return new A.a3(s,r.h("y(1)").a(new A.r8()),r.h("a3<1>")).gn(0)},
gfO(){var s=this.d
if(s.length===0)return B.bl
if(B.b.bG(s,new A.r4()))return B.K
if(B.b.bG(s,new A.r5()))return B.t
if(B.b.bG(s,new A.r6()))return B.D
return B.C},
l(a){var s=this,r=null,q=s.ji(),p=s.jK(a),o=t.i,n=A.a([new A.c(r,"reactor-nav",r,r,r,A.a([new A.c(r,"reactor-nav-label",r,r,r,A.a([new A.k("Fleet",r)],o),r),s.c8(a,"Overview","/",A.HE()),s.c8(a,"Add Server","/add-server",A.Hx()),s.c8(a,"Alerts","/alerts",A.Hu()),s.c8(a,"Comparison","/comparison",A.HB()),s.c8(a,"Settings","/settings",A.HL())],o),r)],o),m=s.d
if(m.length!==0)n.push(s.l8(a))
if(s.gke())n.push(new A.mv(m,s.e,r))
if(s.gdW()!=null){m=s.gdW()
m.toString
n.push(s.lb(a,m))}return new A.j6(new A.dG(n,q,p,!1,r),new A.c(r,"reactor-shell-content",B.ln,r,r,A.a([s.f],o),r),r)},
gdW(){var s,r,q,p,o=this.d
if(o.length===0)return null
s=this.l7()
if(s!=null)for(r=o.length,q=0;q<r;++q){p=o[q]
if(p.a===s)return p}return B.b.gaz(o)},
l7(){var s=t.cF,r=A.x(new A.a3(A.a(this.r.split("/"),t.s),t.dA.a(new A.ra()),s),s.h("m.E"))
if(r.length<2||B.b.gaz(r)!=="server")return null
if(1>=r.length)return A.f(r,1)
return r[1]},
l9(){var s,r,q="overview",p=t.cF,o=A.x(new A.a3(A.a(this.r.split("/"),t.s),t.dA.a(new A.rb()),p),p.h("m.E"))
if(o.length<3||B.b.gaz(o)!=="server")return q
if(2>=o.length)return A.f(o,2)
s=o[2]
for(p=$.zx(),r=0;r<21;++r)if(p[r].b===s)return s
return q},
l8(a){var s,r,q,p=null,o=this.l9(),n=this.d,m=n.length,l=m===1?"Server":"Servers",k=t.i
m=A.a([new A.k(l+" ("+m+")",p)],k)
s=A.a([],k)
for(r=n.length,q=0;q<n.length;n.length===r||(0,A.I)(n),++q)s.push(this.kQ(a,n[q],o))
return new A.c(p,"reactor-server-list",p,p,p,A.a([new A.c(p,"reactor-server-list-label",p,p,p,m,p),new A.c(p,"reactor-server-list-scroll",p,p,p,s,p)],k),p)},
kQ(a,b,c){var s,r,q,p=null,o=this.gdW()
o=o==null?p:o.a
o=o===b.a?"reactor-server-row active":"reactor-server-row"
s=A.j(["click",new A.rc(a,b,c)],t.N,t.v)
r=b.c
q=t.i
return A.fE(A.a([new A.fc(r,p),A.H(A.a([new A.k(b.b,p)],q),p,"reactor-server-row-name",p,p),A.H(A.a([new A.k(A.B7(r),p)],q),p,"reactor-server-row-state",p,p)],q),B.ae,o,s,p,p,p)},
c8(a,b,c,d){return new A.is(b,t.o0.a(d),this.r===c,new A.r9(a,c),null)},
ji(){var s,r,q,p=this,o=null,n=p.gfO()
switch(n.a){case 0:s="Healthy"
break
case 1:s="Warn"
break
case 2:s="Critical"
break
case 3:s="Syncing"
break
case 4:s="Standby"
break
default:s=o}r=p.d
q=r.length===0?"0 paired":""+p.gfU()+"/"+r.length+" live"
r=t.i
return new A.c(o,"reactor-brand",o,o,o,A.a([new A.c(o,"reactor-brand-mark",o,o,o,A.a([new A.a6("e038",B.a_,o)],r),o),new A.c(o,"reactor-brand-body",o,o,o,A.a([new A.c(o,"reactor-brand-title-row",o,o,o,A.a([A.H(A.a([new A.k("Reactor",o)],r),o,"reactor-brand-title",o,o),A.H(A.a([A.dC(n,o,6),new A.k(s,o)],r),o,"reactor-brand-chip",o,o)],r),o),A.H(A.a([new A.k("Fleet Monitor",o)],r),o,"reactor-brand-subtitle",o,o)],r),o),new A.c(o,"reactor-brand-meta",o,o,o,A.a([p.fC("State",s),p.fC("Fleet",q)],r),o)],r),o)},
fC(a,b){var s=null,r=t.i
return new A.c(s,"reactor-brand-stat",s,s,s,A.a([A.H(A.a([new A.k(a,s)],r),s,"reactor-brand-stat-label",s,s),A.H(A.a([new A.k(b,s)],r),s,"reactor-brand-stat-value",s,s)],r),s)},
jK(a){var s,r,q,p=null,o=this.gfO(),n=this.d,m=n.length
if(m===0)s="No servers paired"
else{m=this.gfU()
r=n.length
s=""+m+"/"+r+" servers live"
m=r}q=m===0?"Ready for pairing":"Realtime telemetry"
m=t.i
r=A.a([new A.c(p,"reactor-sidebar-status-top",p,p,p,A.a([new A.c(p,"reactor-sidebar-status-copy",p,p,p,A.a([A.H(A.a([new A.k(s,p)],m),p,"reactor-sidebar-status-title",p,p),A.H(A.a([new A.k(q,p)],m),p,"reactor-sidebar-status-subtitle",p,p)],m),p),A.dC(o,p,8)],m),p)],m)
if(n.length===0){n=A.j(["click",new A.r3(a)],t.N,t.v)
r.push(A.fE(A.a([new A.a6("e081",B.aP,p),A.H(A.a([new A.k("Pair Server",p)],m),p,p,p,p)],m),B.ae,"reactor-sidebar-action",n,p,p,p))}return new A.c(p,"reactor-sidebar-status",p,p,p,r,p)},
lb(a,b){var s,r,q,p,o,n,m=null,l=b.c,k=l===B.w||l===B.B,j=t.i
l=A.a([new A.c(m,"reactor-nav-section-header",m,m,m,A.a([new A.fc(l,m),A.H(A.a([new A.k("Workspace",m)],j),m,"reactor-nav-section-name",m,m)],j),m)],j)
for(s=$.zx(),r=this.r,q="/server/"+b.a+"/",p=0;p<21;++p){o=s[p]
l.push(new A.is(o.a,o.c,r===q+o.b,new A.rd(a,b,o),m))}n=new A.c(m,"reactor-nav-section",m,m,m,l,m)
if(!k)return n
return new A.c(m,m,B.lR,m,m,A.a([n],j),m)}}
A.r7.prototype={
$1(a){var s=t.o.a(a).c
return s===B.w||s===B.B},
$S:12}
A.r8.prototype={
$1(a){return t.o.a(a).c===B.N},
$S:12}
A.r4.prototype={
$1(a){return t.o.a(a).c===B.w},
$S:12}
A.r5.prototype={
$1(a){return t.o.a(a).c===B.B},
$S:12}
A.r6.prototype={
$1(a){return t.o.a(a).c===B.ab},
$S:12}
A.ra.prototype={
$1(a){return A.r(a).length!==0},
$S:5}
A.rb.prototype={
$1(a){return A.r(a).length!==0},
$S:5}
A.rc.prototype={
$1(a){A.p(a)
return A.e_(this.a).b9("/server/"+this.b.a+"/"+this.c,null)},
$S:4}
A.r9.prototype={
$0(){return A.e_(this.a).b9(this.b,null)},
$S:0}
A.r3.prototype={
$1(a){A.p(a)
return A.e_(this.a).b9("/add-server",null)},
$S:4}
A.rd.prototype={
$0(){return A.e_(this.a).b9("/server/"+this.b.a+"/"+this.c.b,null)},
$S:0}
A.ma.prototype={
l(a){var s,r=this,q=null,p="reactor-side-body",o="0%",n="standby",m=t.i,l=A.a([new A.c(q,"reactor-first-run-main",q,q,q,A.a([new A.c(q,"reactor-empty-mark",q,q,q,A.a([new A.a6("e038",B.aQ,q)],m),q),new A.c(q,"reactor-empty-kicker",q,q,q,A.a([new A.k("Fleet control plane",q)],m),q),A.CD(A.a([new A.k("No servers connected",q)],m),"reactor-empty-title",q),A.zq(A.a([new A.k("Reactor is standing by for authenticated telemetry. Pair a React server to bring TPS, memory, entity pressure, alerts, and optimization controls into this console.",q)],m),"reactor-empty-copy",q),new A.c(q,"reactor-empty-actions",q,q,q,A.a([A.Ag(!1,"Pair Server",new A.uF(a),B.v),A.bf(!1,!1,"Fleet Settings",new A.uG(a),B.v)],m),q)],m),q),new A.c(q,"reactor-first-run-proof",q,q,q,A.a([r.ev("0","Paired servers"),r.ev("RCT1","Secure pairing"),r.ev("Live","Telemetry stream")],m),q)],m),k=A.iV("Waiting",B.D)
k=r.ez(new A.c(q,p,q,q,q,A.a([r.d0("TPS",o,n),r.d0("Memory",o,n),r.d0("Incidents",o,n),r.d0("Actions",o,"locked")],m),q),"Signal Readiness",k)
s=A.dC(B.D,"Standby",8)
return new A.c(q,"reactor-first-run",q,q,q,A.a([new A.nl("reactor-first-run-hero",l,q),A.zf(A.a([k,r.ez(new A.c(q,p,q,q,q,A.a([r.e9("reactor://fleet waiting for first handshake"),r.e9("samplers idle until a server is paired"),r.e9("alerts queue armed for live telemetry")],m),q),"Console Status",s),r.lk(new A.c(q,p,q,q,q,A.a([new A.c(q,"reactor-step-list",q,q,q,A.a([r.eC("01","Pair","Add the RCT1 server code."),r.eC("02","Verify","Confirm the authenticated handshake."),r.eC("03","Monitor","Open the live server workspace.")],m),q)],m),q),"Connection Flow")],m),q,"reactor-first-run-side",q)],m),q)},
ev(a,b){var s=null,r=t.i
return new A.c(s,"reactor-proof-cell",s,s,s,A.a([A.H(A.a([new A.k(a,s)],r),s,"reactor-proof-value",s,s),A.H(A.a([new A.k(b,s)],r),s,"reactor-proof-label",s,s)],r),s)},
ez(a,b,c){var s=null,r=t.i,q=A.a([A.H(A.a([new A.k(b,s)],r),s,"reactor-side-title",s,s)],r)
if(c!=null)q.push(c)
return new A.c(s,"reactor-side-panel",s,s,s,A.a([new A.c(s,"reactor-side-panel-head",s,s,s,q,s),a],r),s)},
lk(a,b){return this.ez(a,b,null)},
d0(a,b,c){var s=null,r=t.i,q=A.H(A.a([new A.k(a,s)],r),s,s,s,s),p=t.N
p=A.B(A.j(["--signal",b],p,p))
return new A.c(s,"reactor-signal-row",s,s,s,A.a([q,A.H(A.a([A.H(B.n,s,"reactor-signal-fill",s,s)],r),s,"reactor-signal-bar",s,p),A.H(A.a([new A.k(c,s)],r),s,s,s,s)],r),s)},
e9(a){var s=null,r=t.i
return new A.c(s,"reactor-command-line",s,s,s,A.a([A.H(B.n,s,"reactor-command-dot",s,s),A.H(A.a([new A.k(a,s)],r),s,s,s,s)],r),s)},
eC(a,b,c){var s=null,r=t.i
return new A.c(s,"reactor-step",s,s,s,A.a([A.H(A.a([new A.k(a,s)],r),s,"reactor-step-index",s,s),new A.c(s,s,s,s,s,A.a([new A.c(s,"reactor-step-title",s,s,s,A.a([new A.k(b,s)],r),s),new A.c(s,"reactor-step-copy",s,s,s,A.a([new A.k(c,s)],r),s)],r),s)],r),s)}}
A.uF.prototype={
$0(){return A.e_(this.a).b9("/add-server",null)},
$S:0}
A.uG.prototype={
$0(){return A.e_(this.a).b9("/settings",null)},
$S:0}
A.mv.prototype={
l(a){var s=null,r=B.b.bG(this.d,new A.vG()),q=r?B.K:B.t,p=A.iW(q),o=r?"Connection lost":"Connection degraded",n=t.N,m=t.i
return new A.c(s,s,A.B(A.j(["margin","0.25rem 0","padding","0.7rem 0.75rem","display","flex","flex-direction","column","gap","0.6rem","border-radius","0.6rem","border","1px solid color-mix(in srgb, "+p+" 40%, var(--border))","background","color-mix(in srgb, "+p+" 12%, transparent)"],n,n)),s,s,A.a([new A.c(s,s,B.a6,s,s,A.a([A.dC(q,s,8),A.H(A.a([new A.k(o,s)],m),s,s,s,B.kG)],m),s),A.bf(!1,!0,"Reconnect",this.e,B.h)],m),s)}}
A.vG.prototype={
$1(a){return t.o.a(a).c===B.w},
$S:12}
A.bO.prototype={}
A.f3.prototype={
U(){return new A.hI(A.a([],t.jm))}}
A.hI.prototype={
aW(){var s,r,q,p,o,n,m=this
m.bi()
s=m.a.d
m.e!==$&&A.bT()
m.e=s
r=s.a
q=t.N
q=new A.nF(r,A.t(q,t.cs),A.cJ(q))
p=t.gi
q.b=p.a(q.fV("reactor.alerts.acked"))
q.c=p.a(q.fV("reactor.alerts.resolved"))
q.d=q.km()
m.f!==$&&A.bT()
m.f=q
m.r!==$&&A.bT()
m.r=new A.rD(r)
for(s=A.al(s.d,t.C),r=s.length,o=0;o<r;++o){n=s[o]
m.fB(n.a,n.b)}},
ni(a){var s=this.jP(a)
if(s==null)return
this.fB(a,s.b)},
jP(a){var s,r,q,p=this.e
p===$&&A.S()
p=A.al(p.d,t.C)
s=p.length
r=0
for(;r<s;++r){q=p[r]
if(q.a===a)return q}return null},
fB(a,b){var s,r,q,p=this
if(B.b.bG(p.d,new A.qS(a)))return
s=p.e
s===$&&A.S()
r=s.e.j(0,a)
if(r==null)return
q=new A.bO(a,b,r,r.f)
s=r.ax
q.e=t.hr.a(new A.aM(s,A.n(s).h("aM<1>")).bP(new A.qT(p,a,b)))
p.t(new A.qU(p,q))
r.c_()},
n7(a){var s=this,r=s.d,q=B.b.bM(r,new A.r1(a))
if(q<0)return
if(!(q<r.length))return A.f(r,q)
r=r[q].e
r===$&&A.S()
r.W()
r=s.e
r===$&&A.S()
r.J(0,a)
s.t(new A.r2(s,q))},
hB(){var s,r,q,p,o=this
for(s=o.d,r=s.length,q=0;q<s.length;s.length===r||(0,A.I)(s),++q){p=s[q].e
p===$&&A.S()
p.W()}s=o.e
s===$&&A.S()
s.eg()
B.b.O(s.d)
s.e.O(0)
s.f.O(0)
s.r=null
s.a.J(0,"reactor.fleet")
o.t(new A.qZ(o))},
mA(a){var s,r,q,p,o,n,m,l,k,j,i,h,g=this
t.jO.a(a)
for(s=g.d,r=s.length,q=0;q<s.length;s.length===r||(0,A.I)(s),++q){p=s[q].e
p===$&&A.S()
p.W()}s=g.e
s===$&&A.S()
s.mB(a)
o=A.a([],t.jm)
for(r=A.al(s.d,t.C),p=r.length,n=t.hr,q=0;q<p;++q){m=r[q]
l=m.a
k=s.e.j(0,l)
if(k==null)continue
j=new A.bO(l,m.b,k,k.f)
l=k.ax
i=A.n(l).h("aM<1>")
h=i.h("~(1)?").a(new A.r_(g,m))
j.e=n.a(l.cM(i.h("~(1)?").a(h),null,null,!1))
B.b.m(o,j)}g.t(new A.r0(g,o))
for(s=o.length,q=0;q<o.length;o.length===s||(0,A.I)(o),++q)o[q].c.c_()},
h5(a,b,c){var s,r,q
if(c===B.w)A.cu("Server offline",b)
else if(c===B.B){s=$.bD
if(s==null)s=$.bD=new A.cX(A.a([],t.I),A.a([],t.u),B.y)
r=Date.now()
q=s.c
s.ey(new A.cn("toast_"+r,"Connection degraded",null,b,B.jX,5000,null,null,q))}else if(c===B.N)A.dc("Server reconnected",b)
this.t(new A.qX(this,a,c))},
kS(){var s,r,q
for(s=this.d,r=s.length,q=0;q<s.length;s.length===r||(0,A.I)(s),++q)s[q].c.c_()},
aq(){var s,r,q,p
for(s=this.d,r=s.length,q=0;q<s.length;s.length===r||(0,A.I)(s),++q){p=s[q].e
p===$&&A.S()
p.W()}s=this.e
s===$&&A.S()
s.eg()
s.e.O(0)
this.by()},
jn(){var s=this.d,r=A.F(s),q=r.h("E<1,bX>")
s=A.x(new A.E(s,r.h("bX(1)").a(new A.qV()),q),q.h("z.E"))
return s},
l(a){var s=this,r=s.w,q=s.jn(),p=s.d,o=A.F(p),n=o.h("E<1,c0>")
p=A.x(new A.E(p,o.h("c0(1)").a(new A.qY()),n),n.h("z.E"))
return new A.hd(s,r,new A.dN(q,new A.eF(A.EP(A.H5(s.gkR(),p)),null),null),null)},
$iDT:1}
A.qS.prototype={
$1(a){return t.mn.a(a).a===this.a},
$S:29}
A.qT.prototype={
$1(a){return this.a.h5(this.b,this.c,t.x.a(a))},
$S:16}
A.qU.prototype={
$0(){var s=this.a
B.b.m(s.d,this.b);++s.w},
$S:0}
A.r1.prototype={
$1(a){return t.mn.a(a).a===this.a},
$S:29}
A.r2.prototype={
$0(){var s=this.a
B.b.bR(s.d,this.b);++s.w},
$S:0}
A.qZ.prototype={
$0(){var s=this.a
B.b.O(s.d);++s.w},
$S:0}
A.r_.prototype={
$1(a){var s=this.b
return this.a.h5(s.a,s.b,t.x.a(a))},
$S:16}
A.r0.prototype={
$0(){var s=this.a,r=s.d
B.b.O(r)
B.b.B(r,this.b);++s.w},
$S:0}
A.qX.prototype={
$0(){var s=this.a,r=s.d,q=B.b.bM(r,new A.qW(this.b))
if(q>=0){if(!(q<r.length))return A.f(r,q)
r[q].d=this.c}++s.w},
$S:0}
A.qW.prototype={
$1(a){return t.mn.a(a).a===this.a},
$S:29}
A.qV.prototype={
$1(a){var s,r
t.mn.a(a)
s=a.c
r=s.at
s=s.ax
return new A.bX(a.a,a.b,a.d,new A.aM(r,A.n(r).h("aM<1>")),new A.aM(s,A.n(s).h("aM<1>")))},
$S:72}
A.qY.prototype={
$1(a){t.mn.a(a)
return new A.c0(a.a,a.b,a.d)},
$S:73}
A.lp.prototype={
l(a){var s,r,q,p,o,n,m=A.a([],t.i)
for(s=this.c,r=s.length,q=0;q<s.length;s.length===r||(0,A.I)(s),++q){p=s[q]
o=null
n=p.b
o=n
m.push(this.kD(o))}return new A.no("0 0 100 100",B.lw,B.fR,m,null)},
kD(a){var s,r,q,p,o,n,m,l,k,j,i,h,g
t.bd.a(a)
s=J.aT(a)
if(s.gL(a))return A.CJ(B.n,"")
r=s.j(a,0)
q=s.j(a,0)
for(p=s.gC(a);p.p();){o=p.gu()
if(o<r)r=o
if(o>q)q=o}n=q-r
m=s.gn(a)
for(p=n===0,l=m-1,k=m===1,j=0,i="";j<m;++j){h=k?50:j/l*100
g=p?50:(1-(s.j(a,j)-r)/n)*100
if(j>0)i+=" "
i+=B.e.Z(h,2)+","+B.e.Z(g,2)}return A.CJ(B.n,i.charCodeAt(0)==0?i:i)}}
A.ay.prototype={
U(){return new A.iF()}}
A.iF.prototype={
aW(){var s,r=this
r.bi()
s=A.eo(r)
r.d!==$&&A.bT()
r.d="uplot-"+s
r.r=!0
A.t8(B.Z,r.ghq())},
lA(){var s,r,q,p,o=this
if(!o.r)return
s=o.d
s===$&&A.S()
r=o.a
q=r.c
p=A.F2(r.d,s,q)
if(p==null)o.t(new A.wJ(o))
else o.t(new A.wK(o,p))},
bq(a){var s,r,q=this
q.c0(t.aO.a(a))
s=q.e
if(s!=null){r=t.bi.a(q.a.c)
s.a.setData(A.BW(r))}else{s=q.f
if(!s)A.t8(B.Z,q.ghq())}},
aq(){var s,r=this
r.r=!1
s=r.e
if(s!=null)s.a.destroy()
r.e=null
r.by()},
l(a){var s,r=this,q=null,p=""+r.a.d+"px",o=r.f
if(o){o=t.N
return new A.c(q,q,A.B(A.j(["width","100%","height",p],o,o)),q,q,A.a([new A.lp(r.a.c,q)],t.i),q)}o=r.d
o===$&&A.S()
s=t.N
return new A.c(o,q,A.B(A.j(["width","100%","height",p,"position","relative"],s,s)),q,q,B.n,q)}}
A.wJ.prototype={
$0(){this.a.f=!0},
$S:0}
A.wK.prototype={
$0(){this.a.e=this.b},
$S:0}
A.xd.prototype={
$1(a){return A.x9(a)},
$S:46}
A.xe.prototype={
$1(a){return A.x9(a)},
$S:46}
A.tg.prototype={}
A.y7.prototype={
$1(a){return A.EF(t.C.a(a),A.Er(this.a))},
$S:75}
A.cr.prototype={
a0(){var s=this
return A.j(["key",s.a,"label",s.b,"type",s.c.gdK(),"required",s.d,"default",s.e,"options",s.f],t.N,t.z)}}
A.nx.prototype={
$1(a){return A.r(a)},
$S:11}
A.da.prototype={
a0(){var s=this,r=s.e,q=A.F(r),p=q.h("E<1,L<b,@>>")
r=A.x(new A.E(r,q.h("L<b,@>(1)").a(new A.nw()),p),p.h("z.E"))
return A.j(["id",s.a,"name",s.b,"description",s.c,"destructive",s.d,"params",r],t.N,t.z)}}
A.nv.prototype={
$1(a){return A.Dr(t.P.a(a))},
$S:77}
A.nw.prototype={
$1(a){return t.fS.a(a).a0()},
$S:157}
A.j_.prototype={
a0(){return A.j(["ticketId",this.a,"status",this.b],t.N,t.z)}}
A.cs.prototype={
E(){return"AlertSeverity."+this.b}}
A.bm.prototype={
N(a,b){if(b==null)return!1
return b instanceof A.bm&&b.a+"/"+b.c===this.a+"/"+this.c},
gI(a){return B.a.gI(this.a+"/"+this.c)}}
A.fL.prototype={
a0(){var s=this
return A.j(["tpsWarn",s.a,"tpsCrit",s.b,"msptWarn",s.c,"incidentScoreWarn",s.d,"gcPercentWarn",s.e,"pingP95Warn",s.f,"memoryPressureWarn",s.r],t.N,t.z)}}
A.cy.prototype={
a0(){var s=this.b,r=A.F(s),q=r.h("E<1,L<b,@>>")
s=A.x(new A.E(s,r.h("L<b,@>(1)").a(new A.on()),q),q.h("z.E"))
return A.j(["name",this.a,"nodes",s],t.N,t.z)}}
A.om.prototype={
$1(a){return A.Ay(t.P.a(a))},
$S:57}
A.on.prototype={
$1(a){return t.mv.a(a).a0()},
$S:43}
A.h0.prototype={
a0(){var s=this.a,r=A.F(s),q=r.h("E<1,L<b,@>>")
s=A.x(new A.E(s,r.h("L<b,@>(1)").a(new A.oq()),q),q.h("z.E"))
return A.j(["sections",s],t.N,t.z)}}
A.op.prototype={
$1(a){return A.DE(t.P.a(a))},
$S:81}
A.oq.prototype={
$1(a){return t.ap.a(a).a0()},
$S:82}
A.bw.prototype={
a0(){var s=this,r=s.f,q=A.F(r),p=q.h("E<1,L<b,@>>")
r=A.x(new A.E(r,q.h("L<b,@>(1)").a(new A.oz()),p),p.h("z.E"))
return A.j(["id",s.a,"name",s.b,"category",s.c,"enabled",s.d,"description",s.e,"knobs",r],t.N,t.z)},
hC(a,b){var s,r,q=this
t.ga.a(b)
s=a==null?q.d:a
r=b==null?q.f:b
return new A.bw(q.a,q.b,q.c,s,q.e,r)},
m5(a){return this.hC(a,null)},
m6(a){return this.hC(null,a)},
nn(a,b){var s=this.f,r=A.F(s),q=r.h("E<1,aR>")
s=A.x(new A.E(s,r.h("aR(1)").a(new A.oA(a,b)),q),q.h("z.E"))
return this.m6(s)}}
A.oy.prototype={
$1(a){return A.Ay(t.P.a(a))},
$S:57}
A.oz.prototype={
$1(a){return t.mv.a(a).a0()},
$S:43}
A.oA.prototype={
$1(a){t.mv.a(a)
if(a.a===this.a)return a.m7(this.b)
return a},
$S:83}
A.eD.prototype={
a0(){return this.a}}
A.eJ.prototype={}
A.dO.prototype={}
A.cC.prototype={
lY(a,b){var s,r,q,p
for(s=this.x,r=s.length,q=0;q<r;++q){p=s[q]
if(p.a===a&&p.b===b)return p}return null}}
A.pu.prototype={
$1(a){t.P.a(a)
return new A.eJ(B.e.bv(A.at(a.j(0,"x"))),B.e.bv(A.at(a.j(0,"z"))),A.at(a.j(0,"score")))},
$S:84}
A.k_.prototype={
a0(){var s=this
return A.j(["serverName",s.a,"version",s.b,"folia",s.c,"serverId",s.d],t.N,t.z)}}
A.cE.prototype={
a0(){return A.j(["name",this.a,"weight",this.b,"value",this.c],t.N,t.z)}}
A.eM.prototype={
a0(){var s=this,r=s.d,q=A.F(r),p=q.h("E<1,L<b,@>>")
r=A.x(new A.E(r,q.h("L<b,@>(1)").a(new A.pV()),p),p.h("z.E"))
return A.j(["score",s.a,"state",s.b,"timeline",s.c,"contributors",r],t.N,t.z)}}
A.pT.prototype={
$1(a){return A.r(a)},
$S:11}
A.pU.prototype={
$1(a){t.P.a(a)
return new A.cE(A.r(a.j(0,"name")),A.at(a.j(0,"weight")),A.at(a.j(0,"value")))},
$S:85}
A.pV.prototype={
$1(a){return t.l4.a(a).a0()},
$S:86}
A.dR.prototype={
E(){return"KnobType."+this.b},
gdK(){switch(this.a){case 0:return"bool"
case 1:return"int"
case 2:return"double"
case 3:return"string"
case 4:return"enum"}}}
A.aR.prototype={
a0(){var s=this
return A.j(["key",s.a,"label",s.b,"type",s.c.gdK(),"value",s.d,"options",s.e,"doc",s.f],t.N,t.z)},
m7(a){var s=this
return new A.aR(s.a,s.b,s.c,a,s.e,s.f)},
gfn(){var s=this.d
s=s==null?null:J.aF(s)
return s==null?"":s}}
A.q8.prototype={
$1(a){return A.r(a)},
$S:11}
A.f6.prototype={}
A.hK.prototype={}
A.rf.prototype={
$1(a){return A.r(a)},
$S:11}
A.kS.prototype={
a0(){var s=this
return A.j(["id",s.a,"name",s.b,"suffix",s.c,"value",s.d,"display",s.e,"min",s.f,"max",s.r,"history",s.w],t.N,t.z)}}
A.rw.prototype={
$1(a){return A.at(a)},
$S:87}
A.bq.prototype={
a0(){var s=this
return A.j(["id",s.a,"label",s.b,"host",s.c,"port",s.d,"bearer",s.e,"secure",s.f,"relayUrl",s.r,"serverPubKey",s.w,"fingerprint",s.x],t.N,t.z)}}
A.b9.prototype={}
A.hG.prototype={
E(){return"PressureMode."+this.b},
gdK(){switch(this.a){case 0:return"NORMAL"
case 1:return"PRESSURE"
case 2:return"PANIC"}}}
A.c2.prototype={
a0(){var s=this
return A.j(["name",s.a,"pressureMode",s.b.gdK(),"budgetMs",s.c,"panicMs",s.d,"releaseMs",s.e],t.N,t.z)}}
A.j0.prototype={
jU(a){var s=this.w,r=s!=null,q=r&&!B.b.v(s.b,"op:execute"),p=a.d&&r&&!B.b.v(s.b,"admin"),o=q||p,n=A.o4(o,"Execute",o?null:new A.ny(this,a),B.v)
if(p)return A.A9(n,"Requires admin role")
return n},
fG(a){var s,r,q,p,o,n,m,l,k=A.t(t.N,t.X)
for(s=a.e,r=s.length,q=this.r,p=a.a,o=0;o<s.length;s.length===r||(0,A.I)(s),++o){n=s[o]
m=n.a
l=q.j(0,p)
l=(l==null?B.a0:l).j(0,m)
k.i(0,m,l==null?n.e:l)}return k},
l(a){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e=this,d=null,c=t.i,b=A.a([],c)
for(s=J.aE(e.d),r=e.f,q=e.r;s.p();){p=s.gu()
o=p.b
n=A.a([A.lr(o),A.B8(p.c,B.bw,B.aM)],c)
if(p.d)n.push(B.c4)
for(m=p.e,l=m.length,k=p.a,j=0;j<m.length;m.length===l||(0,A.I)(m),++j){i=m[j]
h=i.a
g=q.j(0,k)
g=(g==null?B.a0:g).j(0,h)
if(g==null)g=i.e
n.push(new A.dQ(new A.aR(h,i.b,i.c,g,i.f,""),new A.nz(e,p,i),!1,d))}if(r===k)n.push(new A.fM("Confirm "+o,"This is a destructive action. Are you sure you want to proceed?","Execute",new A.nA(e,p),new A.nB(e),!0,d))
n.push(e.jU(p))
b.push(A.ez(new A.c(d,d,B.a3,d,d,n,d),!0))}b=A.J(A.aK(b,"220px"),d,d,!1,"Actions",d)
s=A.a([],c)
r=e.e
q=r.length
if(q===0)s.push(new A.c(d,d,B.i,d,d,A.a([new A.k("No actions executed yet.",d)],c),d))
for(j=0;j<q;++j){f=r[j]
s.push(new A.c(d,d,B.lj,d,d,A.a([new A.k(f.a+" \u2014 "+f.c+" \u2014 "+f.b,d)],c),d))}return A.bJ(A.a([b,A.J(new A.c(d,d,B.bA,d,d,s,d),d,d,!1,"Recent Executions",d)],c),20)}}
A.ny.prototype={
$0(){var s=this.b,r=this.a
if(s.d)r.y.$1(s.a)
else r.x.$3(s.a,r.fG(s),!1)},
$S:0}
A.nz.prototype={
$1(a){var s=this.a.z.$3(this.b.a,this.c.a,a)
return s},
$S:14}
A.nA.prototype={
$0(){var s=this.a,r=this.b
s.x.$3(r.a,s.fG(r),!0)
s.y.$1(null)},
$S:0}
A.nB.prototype={
$0(){var s=this.a.y.$1(null)
return s},
$S:0}
A.er.prototype={
U(){return new A.lL(A.t(t.N,t.G))}}
A.lL.prototype={
a5(){var s,r,q=this
q.ar()
s=q.c.H(t.Y)
r=s==null?null:s.d
if(r!=null&&!q.f){q.f=!0
q.d=r
s=new A.nC(r,new A.tB(q),new A.tC(),B.dv,A.a([],t.cP))
q.e=s
s.T()}},
l(a){var s,r,q,p=this,o=null,n="Actions",m="Operational commands"
if(p.d==null){s=t.i
return A.a2(o,A.a([A.J(new A.c(o,o,B.i,o,o,A.a([new A.k("Actions require a live connection.",o)],s),o),o,o,!1,n,o)],s),o,m,n)}r=p.e
if(r.e&&J.eq(r.d)){s=t.i
return A.a2(o,A.a([A.J(new A.c(o,o,B.i,o,o,A.a([new A.k("Loading actions...",o)],s),o),o,o,!1,n,o)],s),o,m,n)}s=a.H(t.U)
q=s==null?o:s.d
return A.a2(o,A.a([new A.j0(r.d,A.al(r.r,t.ej),p.r,p.w,q,new A.tx(r),new A.ty(p),new A.tz(p),o)],t.i),new A.cO(q,o),m,n)}}
A.tB.prototype={
$0(){var s=this.a
return s.t(new A.tA(s))},
$S:0}
A.tA.prototype={
$0(){var s,r=this.a,q=r.e
if(q!=null&&A.al(q.r,t.ej).length>r.x){s=t.ej
r.x=A.al(q.r,s).length
if(A.al(q.r,s).length!==0)A.dc("Action queued",B.b.gaz(A.al(q.r,s)).b)}},
$S:0}
A.tC.prototype={
$1(a){return A.cu("Action failed",J.aF(a))},
$S:10}
A.tx.prototype={
$3(a,b,c){this.a.dj(a,t.G.a(b),c)},
$S:89}
A.ty.prototype={
$1(a){var s=this.a
return s.t(new A.tw(s,a))},
$S:90}
A.tw.prototype={
$0(){return this.a.r=this.b},
$S:0}
A.tz.prototype={
$3(a,b,c){var s=this.a
s.t(new A.tv(s,a,b,c))},
$S:91}
A.tv.prototype={
$0(){var s=this,r=s.a,q=t.N,p=r.w=A.ce(r.w,q,t.G),o=s.b,n=p.j(0,o)
if(n==null)n=A.t(q,t.X)
p.i(0,o,A.ce(n,q,t.X))
r.w.j(0,o).i(0,s.c,s.d)},
$S:0}
A.qG.prototype={}
A.es.prototype={
U(){return new A.i0()}}
A.i0.prototype={
kt(a){var s=a.aG(0)
this.t(new A.tE(this,a,s.gL(s)?null:A.AH(a)))},
jA(){this.t(new A.tD(this))},
l_(){var s,r=this,q=r.c
q.toString
s=A.cA(q)
if(s==null)return
if(!r.w){q=s.e
q===$&&A.S()
q=A.al(q.d,t.C).length!==0}else q=!1
if(q){r.t(new A.tK(r))
return}s.hB()
r.t(new A.tL(r))
A.dc("Fleet reset",null)},
c9(a){var s=0,r=A.Q(t.H),q,p=2,o=[],n=[],m=this,l,k,j,i,h,g,f,e,d
var $async$c9=A.R(function(b,c){if(b===1){o.push(c)
s=p}for(;;)switch(s){case 0:g=m.d
f=g
e=A.AH(f)
if(e!=null){m.t(new A.tF(m,f,e))
s=1
break}m.t(new A.tG(m,f))
p=4
s=7
return A.G(A.nD(f,m.a.d),$async$c9)
case 7:l=c
m.a.d.r=l.a
j=m.c
j.toString
j=A.cA(j)
if(j!=null)j.ni(l.a)
A.dc("Server paired",null)
j=m.c
j.toString
i=l.a
A.e_(j).b9("/server/"+i+"/overview",null)
n.push(6)
s=5
break
case 4:p=3
d=o.pop()
j=A.a1(d)
if(j instanceof A.bI){if(m.c==null){n=[1]
s=5
break}m.t(new A.tH(m))
A.cu("Invalid pairing code","Check that you copied the full RCT1 code from the server console.")}else{k=j
if(m.c==null){n=[1]
s=5
break}m.t(new A.tI(m))
A.cu("Pairing failed",J.aF(k))}n.push(6)
s=5
break
case 3:n=[2]
case 5:p=2
if(m.c!=null)m.t(new A.tJ(m))
s=n.pop()
break
case 6:case 1:return A.O(q,r)
case 2:return A.N(o.at(-1),r)}})
return A.P($async$c9,r)},
h2(){return this.c9(null)},
l(a){var s,r,q,p,o,n,m,l,k,j,i,h,g=this,f=null,e=g.e,d=A.cA(a)
if(d==null)s=f
else{r=d.e
r===$&&A.S()
r=A.al(r.d,t.C).length
s=r}if(s==null)s=0
r=g.x
q=!r
p=q&&B.a.aG(g.d).length!==0
o=g.f
n=!q||g.d.length===0
n=A.bf(n,!1,"Clear code",!q||g.d.length===0?f:g.gjz(),B.h)
m=g.w?"Confirm reset":"Reset fleet"
l=!q||s===0
m=A.yt(l,m,!q||s===0?f:g.gkZ(),B.h)
r=r?"Connecting...":"Pair"
q=p?new A.tM(g):f
l=t.i
q=A.a([n,m,A.Ag(!p,r,q,B.h)],l)
r=g.lo(e,o)
n=o==null
m=!n
if(m)k=B.t
else k=e==null?B.D:B.C
k=A.a([A.dC(k,f,7),A.H(A.a([new A.k("RCT1 handshake",f)],l),f,f,f,f)],l)
if(m)m="Needs full code"
else m=e==null?"Standby":"Decoded"
m=A.a([new A.c(f,"reactor-add-console-title",f,f,f,k,f),A.H(A.a([new A.k(m,f)],l),f,f,f,B.z)],l)
k=A.a([A.cW(!1,o,!0,"You can paste the raw RCT1 token or the full console line.",f,g.gks(),g.gkw(),"Paste RCT1. code from server console",B.V,g.d)],l)
j=g.r
if(j!=null){n=n?"reactor-add-message info":"reactor-add-message warning"
k.push(new A.c(f,n,f,f,f,A.a([new A.k(j,f)],l),f))}k.push(g.jJ(e))
r=A.J(f,A.a([new A.c(f,"reactor-add-console",f,f,f,A.a([new A.c(f,"reactor-add-console-head",f,f,f,m,f),new A.c(f,"reactor-add-console-body",f,f,f,k,f)],l),f)],l),"Connect direct LAN nodes or relay-backed servers.",!1,"Pairing Console",r)
n=A.J(f,A.a([new A.c(f,"reactor-add-step-list",f,f,f,A.a([g.ej("01","Copy","Run the React pairing command and copy the full RCT1 value."),g.ej("02","Decode","Reactor checks the transport, token, and confirmation word."),g.ej("03","Monitor","A live server workspace opens as soon as the fleet accepts it.")],l),f)],l),"The dashboard validates locally before opening telemetry.",!1,"Connection Flow",f)
m=A.dC(B.D,"Credential scope",8)
k=g.av("Saved Servers",B.c.k(s))
j=g.av("Format","RCT1")
i=e==null
h=g.av("Token",i?"Hidden until decoded":"Bearer")
return A.a2(new A.c(f,"reactor-add-actions",f,f,f,q,f),A.a([new A.c(f,"reactor-add-layout",f,f,f,A.a([r,new A.c(f,"reactor-add-side",f,f,f,A.a([n,A.J(f,A.a([k,j,h,g.av("Transport",(i?f:e.f)==null?"Direct host":"Relay channel")],l),"Pairing credentials stay in browser storage for this console.",!1,"Security",m)],l),f)],l),f)],l),f,"Paste an authenticated RCT1 code from the React server console","Add Server")},
lo(a,b){if(b!=null)return A.iV("Check Code",B.t)
if(a==null)return A.iV("Awaiting Code",B.D)
return A.iV("Code Ready",B.C)},
jJ(a){var s,r,q,p,o=this,n=null,m="reactor-add-detail-grid"
if(a==null)return new A.c(n,m,n,n,n,A.a([o.av("Status","Waiting for code"),o.av("Expected","RCT1. payload"),o.av("Validation","Local decode"),o.av("Handshake","One server")],t.i),n)
s=a.a
s=o.av("Host",s.length===0?"Relay only":s)
r=a.b
r=o.av("Port",r<=0?"Relay":B.c.k(r))
q=o.av("Confirm Word",a.e)
p=a.f
p=p==null?n:p.length===0
return new A.c(n,m,n,n,n,A.a([s,r,q,o.av("Relay",p!==!1?"Not used":"Enabled")],t.i),n)},
av(a,b){var s=null,r=t.i
return new A.c(s,"reactor-add-detail",s,s,s,A.a([A.H(A.a([new A.k(a,s)],r),s,"reactor-add-detail-label",s,s),A.H(A.a([new A.k(b,s)],r),s,"reactor-add-detail-value",s,s)],r),s)},
ej(a,b,c){var s=null,r=t.i
return new A.c(s,"reactor-add-step",s,s,s,A.a([A.H(A.a([new A.k(a+" / "+b,s)],r),s,"reactor-add-step-label",s,s),new A.c(s,"reactor-add-step-copy",s,s,s,A.a([new A.k(c,s)],r),s)],r),s)}}
A.tE.prototype={
$0(){var s=this.a,r=this.b
s.d=r
s.e=A.ky(r)
s.f=this.c
s.r=null
s.w=!1},
$S:0}
A.tD.prototype={
$0(){var s=this.a
s.d=""
s.r=s.f=s.e=null
s.w=!1},
$S:0}
A.tK.prototype={
$0(){return this.a.w=!0},
$S:0}
A.tL.prototype={
$0(){var s=this.a
s.w=!1
s.r="Saved fleet cleared. Paste a new RCT1 code to reconnect."},
$S:0}
A.tF.prototype={
$0(){var s=this.a,r=this.b
s.d=r
s.e=A.ky(r)
s.r=s.f=this.c},
$S:0}
A.tG.prototype={
$0(){var s=this.a,r=this.b
s.d=r
s.e=A.ky(r)
s.f=null
s.r="Connecting to server identity endpoint..."
s.x=!0},
$S:0}
A.tH.prototype={
$0(){var s=this.a
s.f=s.r="Invalid pairing code. Copy the full RCT1 line from the server console."},
$S:0}
A.tI.prototype={
$0(){this.a.r="Could not connect to the server API. Verify the host, port, token, and that the web controller is reachable."},
$S:0}
A.tJ.prototype={
$0(){this.a.x=!1},
$S:0}
A.tM.prototype={
$0(){return this.a.h2()},
$S:0}
A.db.prototype={
U(){return new A.lO()}}
A.lO.prototype={
l(a3){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e,d,c=this,b=null,a="Alerts",a0=a3.H(t.T),a1=A.cA(a3),a2=a0==null?b:a0.d
if(a2==null)a2=A.a([],t.bk)
if(a1==null)s=b
else{r=a1.f
r===$&&A.S()
s=r}r=A.F(a2)
q=r.h("E<1,+id,name,snapshot(b,b,b9?)>")
p=A.x(new A.E(a2,r.h("+id,name,snapshot(b,b,b9?)(1)").a(new A.tU()),q),q.h("z.E"))
q=s==null
if(q)o=b
else{n=s.d
n===$&&A.S()
o=n}if(o==null)o=B.F
m=A.yp(new A.b6(Date.now(),0,!1),p,o)
l=q?b:s.hZ(m)
if(l==null)l=m
if(c.d!=null){n=A.F(l)
k=n.h("a3<1>")
l=A.x(new A.a3(l,n.h("y(1)").a(new A.tV(c)),k),k.h("m.E"))}B.b.ai(l,new A.tW())
n=r.h("E<1,b>")
j=A.x(new A.E(a2,r.h("b(1)").a(new A.tX()),n),n.h("z.E"))
r=t.N
r=A.t(r,r)
for(n=a2.length,i=0;i<a2.length;a2.length===n||(0,A.I)(a2),++i){h=a2[i]
r.i(0,h.a,h.b)}n=t.i
r=A.a([c.k_(j,r)],n)
if(l.length===0)r.push(A.J(new A.c(b,b,B.kX,b,b,A.a([new A.k("No open alerts",b)],n),b),b,b,!1,a,b))
else{n=A.a([],n)
for(k=l.length,i=0;i<l.length;l.length===k||(0,A.I)(l),++i){g=l[i]
if(q)f=b
else{f=s.b
f===$&&A.S()
f=f.v(0,g.a+"/"+g.c)}e=q?b:new A.tY(c,s,g)
d=q?b:new A.tZ(c,s,g)
n.push(new A.lN(g,f===!0,e,d,b))}r.push(A.J(new A.c(b,b,B.a4,b,b,n,b),b,b,!1,a,b))}return A.a2(b,r,b,"Open fleet alerts",a)},
k_(a,b){var s,r,q,p,o,n,m=null
t.h.a(a)
t.f.a(b)
s=t.lZ
r=A.a([B.au,B.bT,B.bV,B.bU],s)
s=A.a([B.au],s)
for(q=a.length,p=0;p<a.length;a.length===q||(0,A.I)(a),++p){o=a[p]
n=b.j(0,o)
s.push(new A.ah(n==null?o:n,o,!1))}q=this.d
q=q==null?"":q.b
q=A.nL(new A.tQ(this),r,q)
return new A.c(m,m,B.lu,m,m,A.a([q,A.nL(new A.tR(this),s,"")],t.i),m)}}
A.tU.prototype={
$1(a){t.d.a(a)
return new A.ei(a.a,a.b,a.d)},
$S:28}
A.tV.prototype={
$1(a){return t.e.a(a).d===this.a.d},
$S:37}
A.tW.prototype={
$2(a,b){var s,r=t.e
r.a(a)
r.a(b)
s=b.x.P(0,a.x)
if(s!==0)return s
return B.c.P(A.iT(b.d),A.iT(a.d))},
$S:27}
A.tX.prototype={
$1(a){return t.d.a(a).a},
$S:35}
A.tY.prototype={
$0(){return this.a.t(new A.tT(this.b,this.c))},
$S:0}
A.tT.prototype={
$0(){var s=this.a,r=this.b,q=s.b
q===$&&A.S()
q.m(0,r.a+"/"+r.c)
r=t.gi.a(s.b)
r=A.x(r,A.n(r).c)
s.a.bU("reactor.alerts.acked",B.k.bb(r,null))
return null},
$S:0}
A.tZ.prototype={
$0(){return this.a.t(new A.tS(this.b,this.c))},
$S:0}
A.tS.prototype={
$0(){var s=this.a,r=this.b,q=s.c
q===$&&A.S()
q.m(0,r.a+"/"+r.c)
r=t.gi.a(s.c)
r=A.x(r,A.n(r).c)
s.a.bU("reactor.alerts.resolved",B.k.bb(r,null))
return null},
$S:0}
A.tQ.prototype={
$1(a){var s=this.a
return s.t(new A.tP(s,a))},
$S:2}
A.tP.prototype={
$0(){var s=this.b,r=this.a
if(s.gL(s))r.d=null
else r.d=B.b.hJ(B.dy,new A.tN(s))},
$S:0}
A.tN.prototype={
$1(a){t.eV.a(a)
return!1},
$S:98}
A.tR.prototype={
$1(a){var s=this.a
return s.t(new A.tO(s,a))},
$S:2}
A.tO.prototype={
$0(){var s=this.b
if(s.gL(s))s=null
return this.a.e=s},
$S:0}
A.lN.prototype={
l(a){var s,r=this,q=null,p=r.d,o=t.i,n=A.a([A.F9(p.d),A.H(A.a([new A.k(p.b,q)],o),q,q,q,B.kD),A.H(A.a([new A.k(p.e,q)],o),q,q,q,B.lS)],o),m=A.a([new A.k(p.f,q)],o)
p=A.a([new A.k("First seen: "+A.F8(p.x),q)],o)
s=A.a([],o)
if(!r.e)s.push(A.bf(!1,!1,"Ack",r.f,B.h))
else B.b.B(s,A.a([A.H(A.a([new A.k("Acked",q)],o),q,q,q,B.bJ),A.bf(!1,!1,"Resolve",r.r,B.h)],o))
return A.ez(new A.c(q,q,B.lZ,q,q,A.a([new A.c(q,q,B.aq,q,q,n,q),new A.c(q,q,B.bJ,q,q,m,q),new A.c(q,q,B.at,q,q,p,q),new A.c(q,q,B.kI,q,q,s,q)],o),q),!0)}}
A.jt.prototype={
l(a){var s,r,q,p,o,n,m,l,k,j=null,i=a.H(t.V),h=i==null
if(h)s=j
else{r=i.d
s=r==null?j:r.a.j(0,"chunks")}if(h)q=j
else{r=i.d
q=r==null?j:r.a.j(0,"chunks-loaded")}if(h)p=j
else{r=i.d
p=r==null?j:r.a.j(0,"chunks-generated")}if(h)o=j
else{r=i.d
o=r==null?j:r.a.j(0,"chunk-load-ms")}if(h)n=j
else{r=i.d
n=r==null?j:r.a.j(0,"chunk-gen-ms")}if(h)m=j
else{r=i.d
m=r==null?j:r.a.j(0,"world-save-duration")}if(h)l=j
else{h=i.d
l=h==null?j:h.a.j(0,"pdc-write-batcher")}h=o==null?j:o.w
if(h==null)h=B.j
r=n==null?j:n.w
if(r==null)r=B.j
k=t.i
return A.a2(j,A.a([A.J(new A.ay(A.a([new A.A("Load ms",h),new A.A("Gen ms",r)],t.y),160,j),j,j,!1,"Chunk Load/Gen Time",j),A.aK(A.a([A.Y("Chunks",s),A.Y("Loaded/s",q),A.Y("Generated/s",p),A.Y("Load Time",o),A.Y("Gen Time",n)],k),"220px"),A.J(A.aK(A.a([A.Y("World Save",m),A.Y("PDC Batcher",l)],k),"220px"),j,j,!1,"Persistence",j)],k),j,"Chunk loading and persistence","Chunks")}}
A.dd.prototype={
U(){return new A.lY()}}
A.lY.prototype={
l(a){var s,r,q,p,o,n,m,l,k,j,i=this,h=null,g="No data for selected metric",f=a.H(t.T),e=f==null?h:f.d
if(e==null)e=A.a([],t.bk)
s=i.e
if(s==null){f=A.F(e)
s=new A.E(e,f.h("b(1)").a(new A.uj()),f.h("E<1,b>")).fd(0)}r=A.cJ(t.N)
for(f=e.length,q=0;q<e.length;e.length===f||(0,A.I)(e),++q){p=e[q].d
if(p!=null){p=p.a
r.B(0,new A.aW(p,A.n(p).h("aW<1>")))}}f=t.cF
o=A.x(new A.a3(B.dn,t.dA.a(new A.uk(r)),f),f.h("m.E"))
f=A.F(e)
p=f.h("bi<1,+display,history,id,name,suffix,value(b,q<v>,b,b,b,v)>")
n=A.x(new A.bi(new A.a3(e,f.h("y(1)").a(new A.ul(i,s)),f.h("a3<1>")),f.h("+display,history,id,name,suffix,value(b,q<v>,b,b,b,v)(1)").a(new A.um(i)),p),p.h("m.E"))
m=A.qg(n,!0,t.pa)
B.b.ai(m,new A.un())
f=A.F(n)
p=f.h("E<1,+(b,q<v>)>")
l=A.x(new A.E(n,f.h("+(b,q<v>)(1)").a(new A.uo()),p),p.h("z.E"))
k=n.length===0
f=i.ko(o)
p=i.lc(s,e)
j=A.J(k?new A.c(h,h,B.bF,h,h,A.a([new A.k(g,h)],t.i),h):new A.ay(l,220,h),h,h,!1,"Overlay",h)
return A.a2(f,A.a([p,j,A.J(k?new A.c(h,h,B.bF,h,h,A.a([new A.k(g,h)],t.i),h):i.kl(m),h,h,!0,"Leaderboard",h)],t.i),h,"Cross-server metric comparison","Comparison")},
ko(a){var s,r,q,p,o=null
t.h.a(a)
s=t.i
r=A.H(A.a([new A.k("Metric",o)],s),o,o,o,B.z)
q=A.F(a)
p=q.h("E<1,ah>")
q=A.x(new A.E(a,q.h("ah(1)").a(new A.uf()),p),p.h("z.E"))
return new A.c(o,o,B.bK,o,o,A.a([r,A.nL(new A.ug(this),q,this.d)],s),o)},
lc(a,b){var s,r,q,p,o,n=null
t.cm.a(b)
t.gi.a(a)
s=A.a([],t.i)
for(r=b.length,q=0;q<b.length;b.length===r||(0,A.I)(b),++q){p=b[q]
o=a.v(0,p.a)
s.push(new A.j3(o,p.b,new A.ui(this,a,p),n))}return A.J(new A.c(n,n,B.ll,n,n,s,n),n,n,!1,"Servers",n)},
kl(a){var s,r,q,p,o,n,m,l,k,j=null
t.nt.a(a)
s=t.i
r=A.a([],s)
for(q=t.pa,p=0;p<a.length;p=o){o=p+1
n=q.a(a[p])
m=A.a([new A.k("#"+o,j)],s)
n=n.a
l=A.a([new A.k(n[3],j)],s)
k=n[4]
r.push(new A.c(j,j,B.l4,j,j,A.a([new A.c(j,j,B.lM,j,j,m,j),new A.c(j,j,B.m_,j,j,l,j),new A.c(j,j,B.le,j,j,A.a([new A.k(k.length===0?n[0]:n[0]+" "+k,j)],s),j)],s),j))}return new A.c(j,j,B.a5,j,j,r,j)}}
A.uj.prototype={
$1(a){return t.d.a(a).a},
$S:35}
A.uk.prototype={
$1(a){return this.a.v(0,A.r(a))},
$S:5}
A.ul.prototype={
$1(a){var s,r
t.d.a(a)
if(this.b.v(0,a.a)){s=a.d
if(s==null)s=null
else{r=this.a.d
r=s.a.j(0,r)
s=r}s=s!=null}else s=!1
return s},
$S:58}
A.um.prototype={
$1(a){var s,r,q,p
t.d.a(a)
s=this.a.d
r=a.d.a.j(0,s)
s=r.d
q=r.e
p=r.c
return new A.iu([q,r.w,a.a,a.b,p,s])},
$S:100}
A.un.prototype={
$2(a,b){var s=t.pa
s.a(a)
return B.e.P(s.a(b).a[5],a.a[5])},
$S:101}
A.uo.prototype={
$1(a){var s=t.pa.a(a).a
return new A.A(s[3],s[1])},
$S:102}
A.uf.prototype={
$1(a){A.r(a)
return new A.ah(a,a,!1)},
$S:33}
A.ug.prototype={
$1(a){var s=this.a
return s.t(new A.ue(s,a))},
$S:2}
A.ue.prototype={
$0(){return this.a.d=this.b},
$S:0}
A.ui.prototype={
$1(a){var s=this.a
s.t(new A.uh(s,this.b,a,this.c))},
$S:9}
A.uh.prototype={
$0(){var s=this,r=A.Ep(s.b,t.N),q=s.d.a
if(s.c)r.m(0,q)
else r.J(0,q)
s.a.e=r},
$S:0}
A.jA.prototype={
l(a4){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e,d=this,c="Requires admin role",b="Apply Changes",a=null,a0=d.x,a1=a0?A.A9(A.o4(!0,b,a,B.h),c):A.o4(!1,b,d.w,B.h),a2=t.i,a3=A.a([],a2)
for(s=0;s<4;++s){r={}
q=B.dl[s]
r.a=null
p=q.a
r.a=q.b
o=p
a3.push(a0?new A.fP(A.bf(!0,!1,o,a,B.v),c,a):A.bf(!1,!1,o,new A.ok(r,d),B.v))}a3=A.a([A.J(new A.c(a,a,B.bA,a,a,a3,a),a,a,!1,"Presets",a)],a2)
for(r=d.d.a,q=r.length,n=d.e,s=0;s<r.length;r.length===q||(0,A.I)(r),++s){m=r[s]
l=A.a([],a2)
for(k=m.b,j=k.length,i=0;i<k.length;k.length===j||(0,A.I)(k),++i){h=k[i]
g=h.a
f=n.K(g)?n.j(0,g):h.d
e=a0?a:new A.ol(d,h)
l.push(new A.dQ(new A.aR(g,h.b,h.c,f,h.e,h.f),e,a0,a))}a3.push(new A.e0(m.a,a,a,new A.c(a,a,B.a4,a,a,l,a),a,!1,a))}return A.a2(a1,a3,d.y,"Server configuration","Config Editor")}}
A.ok.prototype={
$0(){var s=this.b.r
return s==null?null:s.$1(this.a.a)},
$S:0}
A.ol.prototype={
$1(a){var s=this.a.f
return s==null?null:s.$2(this.b.a,a)},
$S:14}
A.eA.prototype={
U(){return new A.i8()}}
A.i8.prototype={
a5(){var s,r,q=this
q.ar()
s=q.c.H(t.Y)
r=s==null?null:s.d
if(r!=null&&!q.e){q.e=!0
s=new A.jz(r,new A.uq(q),new A.ur(),B.cy,A.t(t.N,t.X))
q.d=s
s.T()}},
cL(a){var s=0,r=A.Q(t.H),q=this,p
var $async$cL=A.R(function(b,c){if(b===1)return A.N(c,r)
for(;;)switch(s){case 0:p=q.d
p=p==null?null:p.aR(a)
s=2
return A.G(t.p8.b(p)?p:A.Bn(p,t.H),$async$cL)
case 2:p=q.d
if((p==null?null:p.w)==null)A.dc("Configuration applied",null)
return A.O(null,r)}})
return A.P($async$cL,r)},
cK(){var s=0,r=A.Q(t.H),q=this,p
var $async$cK=A.R(function(a,b){if(a===1)return A.N(b,r)
for(;;)switch(s){case 0:p=q.d
p=p==null?null:p.d9()
s=2
return A.G(t.p8.b(p)?p:A.Bn(p,t.H),$async$cK)
case 2:p=q.d
if((p==null?null:p.w)==null)A.dc("Configuration applied",null)
return A.O(null,r)}})
return A.P($async$cK,r)},
l(a){var s,r,q,p,o,n,m=null,l="Config Editor",k="Server configuration",j=this.d
if(j==null){j=t.i
return A.a2(m,A.a([new A.c(m,m,B.i,m,m,A.a([new A.k("Config editing requires a live connection.",m)],j),m)],j),m,k,l)}if(j.f&&j.d.a.length===0){j=t.i
return A.a2(m,A.a([new A.c(m,m,B.i,m,m,A.a([new A.k("Loading configuration...",m)],j),m)],j),m,k,l)}s=a.H(t.U)
r=s==null?m:s.d
q=r!=null&&!B.b.v(r.b,"admin")
s=j.d
p=A.yy(j.e,t.N,t.X)
o=q?m:j.gme()
n=q?m:this.gjd()
j=q||j.e.a===0?m:this.gjc()
return new A.jA(s,p,o,n,j,q,new A.cO(r,m),m)}}
A.uq.prototype={
$0(){return this.a.t(new A.up())},
$S:0}
A.up.prototype={
$0(){},
$S:0}
A.ur.prototype={
$1(a){return A.cu("Config failed",J.aF(a))},
$S:10}
A.jK.prototype={
l(a){var s,r,q,p,o,n,m,l,k=null,j="Entities",i="Ping p95",h=a.H(t.V),g=h==null
if(g)s=k
else{r=h.d
s=r==null?k:r.a.j(0,"entities")}if(g)q=k
else{r=h.d
q=r==null?k:r.a.j(0,"entity-ai-active-count")}if(g)p=k
else{r=h.d
p=r==null?k:r.a.j(0,"entities-spawns")}if(g)o=k
else{r=h.d
o=r==null?k:r.a.j(0,"players")}if(g)n=k
else{r=h.d
n=r==null?k:r.a.j(0,"player-ping-p95")}if(g)m=k
else{g=h.d
m=g==null?k:g.a.j(0,"ping-jitter")}g=s==null?k:s.w
if(g==null)g=B.j
r=t.y
l=A.a([new A.A(j,g)],r)
g=n==null?k:n.w
g=A.a([new A.A(i,g==null?B.j:g)],r)
if(m!=null)g.push(new A.A("Jitter",m.w))
r=t.i
return A.a2(k,A.a([A.J(new A.ay(l,160,k),k,k,!1,"Entity Count",k),A.J(new A.ay(g,120,k),k,k,!1,"Player Ping",k),A.aK(A.a([A.Y("Players",o),A.Y(j,s),A.Y("AI Active",q),A.Y("Spawns/s",p),A.Y(i,n),A.Y("Ping Jitter",m)],r),"220px")],r),k,"Entity counts and AI load",j)}}
A.jL.prototype={
l(a){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e=null,d=A.a([],t.s)
for(s=this.d.a,r=A.n(s).h("aW<1>"),q=r.h("m.E"),p=0;p<4;++p){o=B.dp[p]
n=A.x(new A.aW(s,r),q)
if(B.b.v(n,o))B.b.m(d,o)}r=A.x(new A.aW(s,r),q)
q=r.length
p=0
for(;p<r.length;r.length===q||(0,A.I)(r),++p){o=r[p]
if(!B.b.v(d,o))B.b.m(d,o)}r=t.i
q=A.a([],r)
for(n=d.length,p=0;p<d.length;d.length===n||(0,A.I)(d),++p){m=d[p]
l=A.GO(m)
k=A.a([],r)
j=s.j(0,m)
j=(j==null?B.a0:j).gaF().dG(0)
i=j.length
h=0
for(;h<j.length;j.length===i||(0,A.I)(j),++h){g=j[h]
f=g.a
k.push(new A.c(e,e,B.l1,e,e,A.a([new A.ep(e,e,B.kM,e,A.a([new A.k(f,e)],r),e),new A.ep(e,e,B.kQ,e,A.a([new A.k(A.Gg(m,f,g.b),e)],r),e)],r),e))}q.push(new A.e0(l,e,e,new A.c(e,e,B.a5,e,e,k,e),e,!0,e))}return A.bJ(q,16)}}
A.eE.prototype={
U(){return new A.ic()}}
A.ic.prototype={
a5(){var s,r,q=this
q.ar()
s=q.c.H(t.Y)
r=s==null?null:s.d
if(r!=null&&!q.f){q.e=q.f=!0
r.ce().ah(new A.uA(q),t.a).de(new A.uB(q))}},
kU(){var s=this,r=s.c.H(t.Y),q=r==null?null:r.d
if(q==null)return
s.t(new A.uv(s))
q.ce().ah(new A.uw(s),t.a).de(new A.ux(s))},
l(a){var s=null,r="Environment",q="Host and runtime diagnostics",p=a.H(t.Y)
if((p==null?s:p.d)==null){p=t.i
return A.a2(s,A.a([new A.c(s,s,B.i,s,s,A.a([new A.k("Environment data requires a live connection.",s)],p),s)],p),s,q,r)}if(this.e){p=t.i
return A.a2(s,A.a([new A.c(s,s,B.i,s,s,A.a([new A.k("Loading diagnostics...",s)],p),s)],p),s,q,r)}p=this.d
if(p==null){p=t.i
return A.a2(s,A.a([new A.c(s,s,B.i,s,s,A.a([new A.k("No environment data available.",s)],p),s)],p),s,q,r)}return A.a2(A.bf(!1,!1,"Refresh",this.gkT(),B.h),A.a([new A.jL(p,s)],t.i),s,q,r)}}
A.uA.prototype={
$1(a){var s
t.aC.a(a)
s=this.a
if(s.c==null)return
s.t(new A.uz(s,a))},
$S:32}
A.uz.prototype={
$0(){var s=this.a
s.d=this.b
s.e=!1},
$S:0}
A.uB.prototype={
$1(a){var s
A.az(a)
s=this.a
if(s.c==null)return
s.t(new A.uy(s))},
$S:17}
A.uy.prototype={
$0(){var s=this.a
s.d=null
s.e=!1},
$S:0}
A.uv.prototype={
$0(){return this.a.e=!0},
$S:0}
A.uw.prototype={
$1(a){var s
t.aC.a(a)
s=this.a
if(s.c==null)return
s.t(new A.uu(s,a))},
$S:32}
A.uu.prototype={
$0(){var s=this.a
s.d=this.b
s.e=!1},
$S:0}
A.ux.prototype={
$1(a){var s
A.az(a)
s=this.a
if(s.c==null)return
s.t(new A.ut(s))},
$S:17}
A.ut.prototype={
$0(){var s=this.a
s.d=null
s.e=!1},
$S:0}
A.jP.prototype={
l(a){var s,r,q,p,o,n=null,m="Event Time",l=a.H(t.V),k=l==null
if(k)s=n
else{r=l.d
s=r==null?n:r.a.j(0,"event-handles-per-tick")}if(k)q=n
else{r=l.d
q=r==null?n:r.a.j(0,"events-listeners")}if(k)p=n
else{k=l.d
p=k==null?n:k.a.j(0,"event-time")}k=p==null?n:p.w
if(k==null)k=B.j
r=s==null?n:s.w
if(r==null)r=B.j
o=t.i
return A.a2(n,A.a([A.J(new A.ay(A.a([new A.A(m,k),new A.A("Handles/tick",r)],t.y),160,n),n,n,!1,m,n),A.aK(A.a([A.Y("Handles/Tick",s),A.Y("Listeners",q),A.Y(m,p)],o),"220px")],o),n,"Event dispatch and listeners","Events")}}
A.eG.prototype={
U(){return new A.mc()}}
A.mc.prototype={
l(a7){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e,d,c,b,a,a0,a1,a2=this,a3=null,a4=a7.H(t.T),a5=A.cA(a7),a6=a4==null?a3:a4.d
if(a6==null)a6=A.a([],t.bk)
s=a5==null
if(s)r=a3
else{q=a5.f
q===$&&A.S()
r=q}if(s)p=a3
else{s=a5.r
s===$&&A.S()
p=s}s=p==null
o=s?a3:p.lP()
if(o==null)o=A.a([],t.s)
q=a2.d
if(q!=="All"&&!s){s=A.F(a6)
n=s.h("a3<1>")
m=A.x(new A.a3(a6,s.h("y(1)").a(new A.uL(p.iq(q))),n),n.h("m.E"))}else m=a6
s=A.F(m)
q=s.h("E<1,+id,name,snapshot(b,b,b9?)>")
l=A.x(new A.E(m,s.h("+id,name,snapshot(b,b,b9?)(1)").a(new A.uM()),q),q.h("z.E"))
s=r==null
if(s)k=a3
else{q=r.d
q===$&&A.S()
k=q}if(k==null)k=B.F
j=A.yp(new A.b6(Date.now(),0,!1),l,k)
i=s?a3:r.hZ(j)
h=A.DV(i==null?j:i,m)
g=h.r.length
f=h.w.length
if(g===0)e="No servers paired"
else{s=""+g
e=f===0?"All "+s+" servers nominal":s+" servers \xb7 "+f+" need attention"}s=a2.lr(o)
q=h.f
d=q.j(0,B.E)
if(d==null)d=0
c=q.j(0,B.r)
if(c==null)c=0
b=q.j(0,B.X)
if(b==null)b=0
q=t.i
n=A.a([A.nP(""+d+" critical"),A.nQ(""+c+" warning"),A.yr(""+b+" info")],q)
a=h.a
a0=h.b
a1=h.e
return A.a2(s,A.a([A.J(new A.c(a3,a3,B.l2,a3,a3,A.a([A.cb(B.e.Z(a,1),!0,"Mean TPS",20,B.ak,a),A.cb(B.e.Z(a0,1),!0,"Worst TPS",20,B.ak,a0),A.cb(""+a1+"%",!0,"Composite Health",100,B.iT,a1),A.AX("Total Players",a3,B.c.k(h.c)),A.AX("Worst MSPT","ms",B.e.Z(h.d,1))],q),a3),a3,a3,!1,"Fleet Health",new A.c(a3,a3,B.l0,a3,a3,n,a3)),a2.l6(h),a2.kq(h)],q),a3,e,"Fleet")},
lr(a){var s,r,q,p,o=null
t.h.a(a)
s=A.a(["All"],t.s)
B.b.B(s,a)
r=t.i
q=A.H(A.a([new A.k("Tag",o)],r),o,o,o,B.z)
p=t.bO
s=A.x(new A.E(s,t.cl.a(new A.uJ()),p),p.h("z.E"))
return new A.c(o,o,B.bK,o,o,A.a([q,A.nL(new A.uK(this),s,this.d)],r),o)},
l6(a){var s,r,q=a.r,p=q.length,o=A.a([],t.i)
for(s=q.length,r=0;r<q.length;q.length===s||(0,A.I)(q),++r)o.push(new A.mF(q[r],null))
return A.J(A.aK(o,"250px"),null,""+p+" paired",!1,"Servers",null)},
kq(a){var s,r,q=null,p=a.w,o=t.i
if(p.length===0)p=new A.c(q,q,B.m6,q,q,A.a([A.dC(B.C,q,8),new A.k("All servers healthy",q)],o),q)
else{o=A.a([],o)
for(s=p.length,r=0;r<p.length;p.length===s||(0,A.I)(p),++r)o.push(new A.mu(p[r],q))
p=new A.c(q,q,B.a5,q,q,o,q)}return A.J(p,q,q,!1,"Needs Attention",q)}}
A.uL.prototype={
$1(a){return B.b.v(this.a,t.d.a(a).a)},
$S:58}
A.uM.prototype={
$1(a){t.d.a(a)
return new A.ei(a.a,a.b,a.d)},
$S:28}
A.uJ.prototype={
$1(a){A.r(a)
return new A.ah(a,a,!1)},
$S:33}
A.uK.prototype={
$1(a){var s=this.a
return s.t(new A.uI(s,a))},
$S:2}
A.uI.prototype={
$0(){return this.a.d=this.b},
$S:0}
A.mF.prototype={
fY(a,b,c){var s=null,r=t.i,q=A.H(A.a([new A.k(a,s)],r),s,s,s,B.z),p=c==null?"var(--foreground)":A.iW(c),o=t.N
o=A.B(A.j(["font-size","0.95rem","font-weight","600","font-variant-numeric","tabular-nums","color",p],o,o))
return new A.c(s,s,B.lK,s,s,A.a([q,A.H(A.a([new A.k(b,s)],r),s,s,s,o)],r),s)},
em(a,b){return this.fY(a,b,null)},
l(a){var s=this,r=null,q=s.d,p=q.d,o=A.FC(q),n=t.i,m=A.a([new A.c(r,r,B.bD,r,r,A.a([new A.fc(q.c,r),A.H(A.a([new A.k(q.b,r)],n),r,r,r,B.lB)],n),r),A.iV(o.a,o.b)],n),l=s.em("TPS",p!=null?B.e.Z(p,1):"--"),k=s.em("Players",B.c.k(q.f)),j=q.w,i=B.c.k(j)
return A.jp("0.5rem",new A.c(r,r,B.ar,r,r,A.a([new A.c(r,r,B.kT,r,r,m,r),new A.c(r,r,B.m5,r,r,A.a([l,k,s.fY("Alerts",i,j>0?B.t:B.bl),s.em("Last Seen",A.FB(q.x))],n),r),new A.c(r,r,B.l8,r,r,A.a([A.bf(!1,!0,"Open dashboard",new A.vW(s,a),B.h)],n),r)],n),r),!0,"0")}}
A.vW.prototype={
$0(){return A.e_(this.b).b9("/server/"+this.a.d.a+"/overview",null)},
$S:0}
A.mu.prototype={
l(a){var s,r=null,q=this.d,p=A.Fr(q),o=p.b,n=t.i
q=A.a([A.dC(o,r,8),A.H(A.a([new A.k(q.b,r)],n),r,r,r,B.lJ)],n)
s=t.N
s=A.B(A.j(["font-size","0.8rem","font-weight","500","color",A.iW(o)],s,s))
return new A.c(r,r,B.ls,r,r,A.a([new A.c(r,r,B.bD,r,r,q,r),A.H(A.a([new A.k(p.a,r)],n),r,r,r,s)],n),r)}}
A.hg.prototype={
l(a){var s,r,q,p,o,n,m,l,k=this,j=null,i=k.e,h=i==null,g=h?j:i.d
if(g==null)g=0
s=t.i
g=A.a([A.cb(h?j:i.e,!1,"Incident Score",100,B.S,g)],s)
i=A.aK(A.a([A.Y("Scheduler Backlog",k.f),A.Y("Backlog Growth Rate",k.r)],s),"220px")
r=A.a([],s)
for(h=k.d,q=h.length,p=0;p<h.length;h.length===q||(0,A.I)(h),++p){o=h[p]
n=A.lr(o.b)
m=o.d
l=m?B.c_:B.c1
r.push(A.ez(new A.c(j,j,B.a3,j,j,A.a([n,l,A.nR(!1,j,new A.pr(k,o),m)],s),j),!0))}return A.bJ(A.a([new A.c(j,j,B.ap,j,j,g,j),i,A.J(A.aK(r,"220px"),j,j,!1,"Governors",j)],s),20)}}
A.pr.prototype={
$1(a){var s=this.a.w
return s==null?null:s.$2(this.b.a,a)},
$S:9}
A.eH.prototype={
U(){return new A.mh()}}
A.mh.prototype={
a5(){var s,r,q=this
q.ar()
s=q.c.H(t.A)
r=s==null?null:s.d
if(r!=null&&!q.f){q.f=!0
q.d=r
s=new A.eB(r,!1,new A.va(q),new A.vb(),B.R)
q.e=s
s.T()}},
l(a){var s,r,q,p,o,n=null,m="Governors",l="Adaptive load governors",k=a.H(t.V),j=k==null
if(j)s=n
else{r=k.d
s=r==null?n:r.a.j(0,"incident-score")}if(j)q=n
else{r=k.d
q=r==null?n:r.a.j(0,"scheduler-backlog")}if(j)p=n
else{j=k.d
p=j==null?n:j.a.j(0,"backlog-growth-rate")}j=this.e
if(j==null)o=B.R
else{j=J.zE(j.e,new A.v8())
o=A.x(j,j.$ti.h("m.E"))}if(this.d==null){j=t.i
return A.a2(n,A.a([A.J(new A.c(n,n,B.i,n,n,A.a([new A.k("Governor control requires a live connection.",n)],j),n),n,n,!1,"Governor Control",n),new A.hg(B.R,s,q,p,n,n)],j),n,l,m)}return A.a2(n,A.a([new A.hg(o,s,q,p,this.e.gfe(),n)],t.i),n,l,m)}}
A.va.prototype={
$0(){return this.a.t(new A.v9())},
$S:0}
A.v9.prototype={
$0(){},
$S:0}
A.vb.prototype={
$1(a){return A.cu("Update failed",J.aF(a))},
$S:10}
A.v8.prototype={
$1(a){return B.jJ.v(0,t.j.a(a).a)},
$S:6}
A.eK.prototype={
U(){return new A.mi()}}
A.mi.prototype={
a5(){var s,r,q=this
q.ar()
s=q.c.H(t.fI)
r=s==null?null:s.d
if(r!=null&&!q.r){q.r=!0
q.d=r
q.f=!0
A.nd(r).ah(new A.vf(q),t.a).de(new A.vg(q))}},
l(a){var s,r,q,p,o,n,m,l,k,j=this,i=null,h="Chunk Heatmaps",g=a.H(t.V),f=g==null?i:g.d,e=t.i,d=A.a([],e)
for(s=f==null,r=0;r<10;++r){q=B.dz[r]
p=q.a
o=i
n=q.b
o=n
m=p
if(s)l=i
else{A.r(m)
l=f.a.j(0,m)}if(l!=null)B.b.m(d,new A.dr(o,l,i))}s=A.a([],e)
if(d.length!==0)s.push(A.J(A.aK(d,"220px"),i,i,!1,"Spatial Metrics",i))
if(j.f)s.push(A.J(new A.c(i,i,B.i,i,i,A.a([new A.k("Loading heatmaps...",i)],e),i),i,i,!1,h,i))
if(!j.f){q=j.e
q=q!=null&&J.zC(q)}else q=!1
if(q){q=A.a([],e)
k=j.e
k.toString
k=J.aE(k)
while(k.p())q.push(new A.jY(k.gu(),i))
s.push(A.J(A.bJ(q,12),i,i,!1,h,i))}if(!j.f&&j.d==null)s.push(A.J(new A.c(i,i,B.i,i,i,A.a([new A.k("Grid heatmaps require a live connection.",i)],e),i),i,i,!1,h,i))
return A.a2(i,s,i,"Spatial load distribution","Heatmaps")}}
A.vf.prototype={
$1(a){var s
t.iM.a(a)
s=this.a
if(s.c==null)return
s.t(new A.ve(s,a))},
$S:109}
A.ve.prototype={
$0(){var s=this.a
s.e=this.b
s.f=!1},
$S:0}
A.vg.prototype={
$1(a){var s
A.az(a)
s=this.a
if(s.c==null)return
s.t(new A.vd(s))},
$S:17}
A.vd.prototype={
$0(){var s=this.a
s.e=B.ds
s.f=!1},
$S:0}
A.k0.prototype={
l(a){var s,r,q,p,o,n,m,l,k=this,j=null,i="Start by adding your first item.",h="No data yet",g=k.e
if(g==null)g=k.d.a
s=t.i
r=A.a([A.cb(k.f,!1,"Incident Score",100,B.S,g)],s)
q=k.d
p=A.a([A.GS(q.b)],s)
o=q.c
if(o.length===0)o=A.ct(i,h)
else{n=A.a([],s)
for(m=o.length,l=0;l<o.length;o.length===m||(0,A.I)(o),++l)n.push(new A.c(j,j,B.m4,j,j,A.a([new A.k(o[l],j)],s),j))
o=A.bJ(n,4)}o=A.J(o,j,j,!1,"Incident Timeline",j)
q=q.d
if(q.length===0)q=A.ct(i,h)
else{n=A.a([],s)
for(m=q.length,l=0;l<q.length;q.length===m||(0,A.I)(q),++l)n.push(new A.lZ(q[l],j))
q=A.bJ(n,8)}return A.bJ(A.a([new A.c(j,j,B.ap,j,j,r,j),new A.c(j,j,B.lg,j,j,p,j),o,A.J(q,j,j,!1,"Contributing Factors",j)],s),20)}}
A.lZ.prototype={
l(a){var s=null,r=this.d,q=B.e.ac(B.e.a3(r.b,0,1)*100),p=t.i,o=t.N
return new A.c(s,s,B.l6,s,s,A.a([new A.c(s,s,B.m9,s,s,A.a([new A.k(r.a,s),new A.k(B.e.Z(r.c,1),s)],p),s),new A.c(s,s,B.kn,s,s,A.a([new A.c(s,s,A.B(A.j(["height","100%","width",""+q+"%","background-color","var(--primary)","border-radius","2px"],o,o)),s,s,A.a([],p),s)],p),s)],p),s)}}
A.eL.prototype={
U(){return new A.ml()}}
A.ml.prototype={
a5(){var s,r,q=this
q.ar()
s=q.c.H(t.Y)
r=s==null?null:s.d
if(r!=null&&!q.f){q.e=q.f=!0
r.dr().ah(new A.vl(q),t.a).de(new A.vm(q))}},
l(a){var s,r,q,p=null,o="Incident Center",n="Live incident analysis",m=a.H(t.V)
if(m==null)s=p
else{m=m.d
s=m==null?p:m.a.j(0,"incident-score")}m=a.H(t.Y)
if((m==null?p:m.d)==null){m=t.i
return A.a2(p,A.a([A.J(new A.c(p,p,B.i,p,p,A.a([new A.k("Incident data requires a live connection.",p)],m),p),p,p,!1,o,p)],m),p,n,o)}if(this.e){m=t.i
return A.a2(p,A.a([A.J(new A.c(p,p,B.i,p,p,A.a([new A.k("Loading incidents...",p)],m),p),p,p,!1,o,p)],m),p,n,o)}m=this.d
if(m==null)return A.a2(p,A.a([A.J(A.ct("Start by adding your first item.","No data yet"),p,p,!1,o,p)],t.i),p,n,o)
r=s==null
q=r?p:s.d
return A.a2(p,A.a([new A.k0(m,q,r?p:s.e,p)],t.i),p,n,o)}}
A.vl.prototype={
$1(a){var s
t.h6.a(a)
s=this.a
if(s.c==null)return
s.t(new A.vk(s,a))},
$S:110}
A.vk.prototype={
$0(){var s=this.a
s.d=this.b
s.e=!1},
$S:0}
A.vm.prototype={
$1(a){var s
A.az(a)
s=this.a
if(s.c==null)return
s.t(new A.vj(s))},
$S:17}
A.vj.prototype={
$0(){var s=this.a
s.d=null
s.e=!1},
$S:0}
A.k1.prototype={
l(a){var s,r,q,p,o,n,m,l,k=null,j="Incident Score",i="Scheduler Backlog",h=a.H(t.V),g=h==null
if(g)s=k
else{r=h.d
s=r==null?k:r.a.j(0,"incident-score")}if(g)q=k
else{r=h.d
q=r==null?k:r.a.j(0,"backlog-growth-rate")}if(g)p=k
else{g=h.d
p=g==null?k:g.a.j(0,"scheduler-backlog")}g=s==null
r=g?k:s.d
if(r==null)r=0
o=t.i
r=A.a([A.cb(g?k:s.e,!1,j,100,B.S,r)],o)
g=g?k:s.w
if(g==null)g=B.j
n=t.y
g=A.J(new A.ay(A.a([new A.A(j,g)],n),120,k),k,k,!1,"Incident Timeline",k)
m=q==null?k:q.w
if(m==null)m=B.j
l=p==null?k:p.w
if(l==null)l=B.j
return A.a2(k,A.a([new A.c(k,k,B.ap,k,k,r,k),g,A.J(new A.ay(A.a([new A.A("Growth Rate",m),new A.A(i,l)],n),120,k),k,k,!1,"Backlog",k),A.aK(A.a([A.Y(j,s),A.Y("Backlog Growth",q),A.Y(i,p)],o),"220px")],o),k,"Incident scoring and history","Incidents")}}
A.k5.prototype={
l(a){var s,r=null,q="Integrations",p="Detected plugin integrations",o=a.H(t.V),n=o==null?r:o.d,m=A.z9(n,B.aV),l=A.z9(n,B.aT),k=A.z9(n,B.aU)
if(!m&&!l&&!k)return A.a2(r,A.a([A.ct("No Adapt, Iris, or Wormholes metrics are being reported by this server.","No integrations detected")],t.i),r,p,q)
s=A.a([],t.i)
if(m)B.b.m(s,new A.lM(n,r))
if(l)B.b.m(s,new A.mn(n,r))
if(k)B.b.m(s,new A.mS(n,r))
return A.a2(r,s,r,p,q)}}
A.lM.prototype={
l(a){var s,r,q,p,o,n=null,m=t.i,l=A.a([],m)
for(s=this.d,r=0;r<4;++r){q=B.aV[r]
p=s==null?n:s.a.j(0,q)
if(p!=null)B.b.m(l,new A.dr(A.zd(q),p,n))}o=s==null?n:s.a.j(0,"adapt-world-policy-latency")
m=A.a([A.aK(l,"220px")],m)
if(o!=null)m.push(new A.ay(A.a([new A.A("Policy Latency",o.w)],t.y),100,n))
return A.J(A.bJ(m,12),n,n,!1,"Adapt",n)}}
A.mn.prototype={
l(a){var s,r,q,p,o,n=null,m=t.i,l=A.a([],m)
for(s=this.d,r=0;r<3;++r){q=B.aT[r]
p=s==null?n:s.a.j(0,q)
if(p!=null)B.b.m(l,new A.dr(A.zd(q),p,n))}o=s==null?n:s.a.j(0,"iris-chunk-stream-ms")
m=A.a([A.aK(l,"220px")],m)
if(o!=null)m.push(new A.ay(A.a([new A.A("Chunk Stream ms",o.w)],t.y),100,n))
return A.J(A.bJ(m,12),n,n,!1,"Iris",n)}}
A.mS.prototype={
l(a){var s,r,q,p,o,n=null,m=t.i,l=A.a([],m)
for(s=this.d,r=0;r<8;++r){q=B.aU[r]
p=s==null?n:s.a.j(0,q)
if(p!=null)B.b.m(l,new A.dr(A.zd(q),p,n))}o=s==null?n:s.a.j(0,"wormholes-projection-render-ms")
m=A.a([A.aK(l,"220px")],m)
if(o!=null)m.push(new A.ay(A.a([new A.A("Projection Render ms",o.w)],t.y),100,n))
return A.J(A.bJ(m,12),n,n,!1,"Wormholes",n)}}
A.xl.prototype={
$1(a){var s
A.r(a)
s=a.length
if(s===0)return a
if(0>=s)return A.f(a,0)
return a[0].toUpperCase()+B.a.S(a,1)},
$S:23}
A.k7.prototype={
l(a){var s,r,q,p,o,n,m,l,k,j,i,h,g=null,f="Process Load",e=a.H(t.V),d=e==null
if(d)s=g
else{r=e.d
s=r==null?g:r.a.j(0,"react-async-tick-time")}if(d)q=g
else{r=e.d
q=r==null?g:r.a.j(0,"react-sync-tick-time")}if(d)p=g
else{r=e.d
p=r==null?g:r.a.j(0,"react-jobs-queue")}if(d)o=g
else{r=e.d
o=r==null?g:r.a.j(0,"react-job-queue-time")}if(d)n=g
else{r=e.d
n=r==null?g:r.a.j(0,"react-job-budget")}if(d)m=g
else{r=e.d
m=r==null?g:r.a.j(0,"processor-system-load")}if(d)l=g
else{r=e.d
l=r==null?g:r.a.j(0,"processor-process-load")}if(d)k=g
else{d=e.d
k=d==null?g:d.a.j(0,"processor-outside-load")}d=s==null?g:s.w
if(d==null)d=B.j
r=q==null?g:q.w
if(r==null)r=B.j
r=A.J(new A.ay(A.a([new A.A("Async",d),new A.A("Sync",r)],t.y),160,g),g,g,!1,"React Tick Time",g)
d=t.i
j=A.J(A.aK(A.a([A.Y("Queue",p),A.Y("Queue Time",o),A.Y("Budget",n)],d),"220px"),g,g,!1,"Jobs",g)
i=l==null
h=i?g:l.d
if(h==null)h=0
return A.a2(g,A.a([r,j,A.J(new A.c(g,g,B.lX,g,g,A.a([A.cb(i?g:l.e,!1,f,100,B.iX,h),A.aK(A.a([A.Y("System Load",m),A.Y(f,l),A.Y("Outside Load",k)],d),"220px")],d),g),g,g,!1,"CPU Load",g)],d),g,"Engine internals and job queues","Internals")}}
A.kg.prototype={
l(a){var s,r,q,p,o,n=this,m=null,l=n.e?"Resume":"Pause",k=t.i
l=A.a([A.bf(!1,!1,l,new A.qk(n),B.h),A.yu(!1,"Clear",new A.ql(n),B.h)],k)
s=A.yq(!1,m,!1,m,"Level",m,new A.qm(n),B.dh,m,!1,B.A,n.f)
r=A.a([],k)
for(q=n.d,p=q.length,o=0;o<q.length;q.length===p||(0,A.I)(q),++o)r.push(new A.c(m,m,B.kv,m,m,A.a([new A.k(q[o],m)],k),m))
return A.a2(new A.c(m,m,B.a6,m,m,l,m),A.a([A.J(new A.j8(new A.c(m,m,B.a5,m,m,r,m),"480px",m),m,m,!0,"Stream",s)],k),m,"Live server log stream","Logs")}}
A.qk.prototype={
$0(){var s=this.a
s=s.r.$1(!s.e)
return s},
$S:0}
A.ql.prototype={
$0(){var s=this.a.w.$0()
return s},
$S:0}
A.qm.prototype={
$1(a){var s=this.a.x.$1(a)
return s},
$S:2}
A.eT.prototype={
U(){return new A.ms()}}
A.ms.prototype={
a5(){var s,r,q,p,o=this
o.ar()
s=o.c.H(t.Y)
r=s==null
q=r?null:s.d
p=r?null:s.e
if(q!=null&&!o.f){o.f=!0
o.d=q
r=p==null?null:p.$0()
r=new A.qh(q,r,new A.vD(o),A.a([],t.s))
o.e=r
r.T()
o.e.c_()}},
aq(){var s,r=this.e
if(r!=null){s=r.y
if(s!=null)s.W()
r.y=null
r=r.b
if((r==null?null:r.a_())==null)A.pq(null,t.H)}this.by()},
l(a){var s,r,q=this,p=null
if(q.d==null){s=t.i
return A.a2(p,A.a([A.J(new A.c(p,p,B.i,p,p,A.a([new A.k("Logs require a live connection.",p)],s),p),p,p,!1,"Logs",p)],s),p,"Live server log stream","Logs")}r=q.e
return new A.kg(r.gnm(),r.f,r.r,new A.vz(q,r),new A.vA(q,r),new A.vB(q,r),p)}}
A.vD.prototype={
$0(){return this.a.t(new A.vC())},
$S:0}
A.vC.prototype={
$0(){},
$S:0}
A.vz.prototype={
$1(a){return this.a.t(new A.vy(this.b,a))},
$S:9}
A.vy.prototype={
$0(){var s=this.a
s.f=this.b
s.bB()
return null},
$S:0}
A.vA.prototype={
$0(){return this.a.t(new A.vx(this.b))},
$S:0}
A.vx.prototype={
$0(){var s=this.a
B.b.O(s.e)
s.bB()
return null},
$S:0}
A.vB.prototype={
$1(a){return this.a.t(new A.vw(this.b,a))},
$S:2}
A.vw.prototype={
$0(){var s=this.a
s.r=this.b
s.bB()
return null},
$S:0}
A.kh.prototype={
l(a3){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e,d,c=null,b="Redstone",a="Tick Time",a0="220px",a1=a3.H(t.V),a2=a1==null
if(a2)s=c
else{r=a1.d
s=r==null?c:r.a.j(0,"redstone")}if(a2)q=c
else{r=a1.d
q=r==null?c:r.a.j(0,"redstone-burst-rate")}if(a2)p=c
else{r=a1.d
p=r==null?c:r.a.j(0,"redstone-tick-time")}if(a2)o=c
else{r=a1.d
o=r==null?c:r.a.j(0,"hopper")}if(a2)n=c
else{r=a1.d
n=r==null?c:r.a.j(0,"hopper-tick-time")}if(a2)m=c
else{r=a1.d
m=r==null?c:r.a.j(0,"hopper-chain-coalescing")}if(a2)l=c
else{r=a1.d
l=r==null?c:r.a.j(0,"physics")}if(a2)k=c
else{r=a1.d
k=r==null?c:r.a.j(0,"physics-tick-time")}if(a2)j=c
else{r=a1.d
j=r==null?c:r.a.j(0,"fluid")}if(a2)i=c
else{r=a1.d
i=r==null?c:r.a.j(0,"fluid-tick-time")}if(a2)h=c
else{r=a1.d
h=r==null?c:r.a.j(0,"crop-fast-forward")}if(a2)g=c
else{r=a1.d
g=r==null?c:r.a.j(0,"lazy-gravity-skipped")}if(a2)f=c
else{r=a1.d
f=r==null?c:r.a.j(0,"spawner-light-cache-skipped")}if(a2)e=c
else{a2=a1.d
e=a2==null?c:a2.a.j(0,"explosion-packet-reduction")}a2=p==null?c:p.w
d=A.a([new A.A("Redstone Tick Time",a2==null?B.j:a2)],t.y)
a2=t.i
return A.a2(c,A.a([A.J(A.bJ(A.a([A.aK(A.a([A.Y(b,s),A.Y("Burst Rate",q),A.Y(a,p)],a2),a0),new A.ay(d,120,c)],a2),12),c,c,!1,b,c),A.J(A.aK(A.a([A.Y("Hoppers",o),A.Y(a,n),A.Y("Chain Coalescing",m)],a2),a0),c,c,!1,"Hoppers",c),A.J(A.aK(A.a([A.Y("Physics",l),A.Y("Physics Tick Time",k),A.Y("Fluid",j),A.Y("Fluid Tick Time",i)],a2),a0),c,c,!1,"Physics & Fluids",c),A.J(A.aK(A.a([A.Y("Crop Fast-Forward",h),A.Y("Lazy Gravity Skipped",g),A.Y("Spawner Light Cache Skipped",f),A.Y("Explosion Packet Reduction",e)],a2),a0),c,c,!1,"Optimizations",c)],a2),c,"Game mechanic optimizations","Mechanics")}}
A.ki.prototype={
l(a){var s,r,q,p,o,n,m,l,k,j,i,h=null,g="Memory Used",f="Memory Pressure",e="GC Pause p95",d=a.H(t.V),c=d==null
if(c)s=h
else{r=d.d
s=r==null?h:r.a.j(0,"memory-used")}if(c)q=h
else{r=d.d
q=r==null?h:r.a.j(0,"memory-free")}if(c)p=h
else{r=d.d
p=r==null?h:r.a.j(0,"memory-used-after-gc")}if(c)o=h
else{r=d.d
o=r==null?h:r.a.j(0,"memory-pressure")}if(c)n=h
else{r=d.d
n=r==null?h:r.a.j(0,"gc-time-percent")}if(c)m=h
else{r=d.d
m=r==null?h:r.a.j(0,"gc-pause-p95")}if(c)l=h
else{c=d.d
l=c==null?h:c.a.j(0,"memory-garbage")}c=s==null?h:s.w
if(c==null)c=B.j
r=t.y
c=A.a([new A.A(g,c)],r)
if(q!=null)c.push(new A.A("Memory Free",q.w))
if(p!=null)c.push(new A.A("After GC",p.w))
k=o==null?h:o.w
j=A.a([new A.A(f,k==null?B.j:k)],r)
k=m==null?h:m.w
i=A.a([new A.A(e,k==null?B.j:k)],r)
r=t.i
return A.a2(h,A.a([A.J(new A.ay(c,180,h),h,h,!1,"Heap Usage",h),A.J(new A.ay(j,100,h),h,h,!1,f,h),A.J(new A.ay(i,100,h),h,h,!1,e,h),new A.c(h,h,B.bI,h,h,A.a([new A.mg(n,h),A.Y(g,s),A.Y("Memory Garbage",l)],r),h)],r),h,"Heap usage and garbage collection","Memory")}}
A.mg.prototype={
l(a){var s=null,r=t.i,q=A.a([new A.k("GC Time",s)],r),p=this.d,o=p==null,n=o?s:p.d
if(n==null)n=0
return A.ez(new A.c(s,s,B.kE,s,s,A.a([new A.c(s,s,B.as,s,s,q,s),A.cb(o?s:p.e,!1,"GC Time %",100,B.iV,n)],r),s),!0)}}
A.kv.prototype={
l(a){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e=this,d=null,c=A.ke(d,d,t.N,t.iD)
for(s=J.aE(e.d);s.p();){r=s.gu()
J.fJ(c.dz(r.c,new A.qC()),r)}s=e.y
r=A.bf(s,!1,"Enable all",s?d:new A.qD(e),B.h)
q=s?d:new A.qE(e)
p=t.i
q=A.a([r,new A.cv("Disable all",q,B.aD,B.h,s,!1,d)],p)
r=A.a([],p)
for(o=new A.aC(c,A.n(c).h("aC<1,2>")).gC(0),n=e.r,m=e.w;o.p();){l=o.d
k=l.a
j=l.b
i=J.bl(j)
h=i.dJ(j,new A.qF()).gn(0)
g=i.gn(j)
f=A.a([],p)
for(j=i.gC(j);j.p();)f.push(new A.m9(j.gu(),s,n,m,d))
r.push(new A.e0(k,""+h+" of "+g+" on",d,A.aK(f,"260px"),d,!1,d))}return A.a2(new A.c(d,d,B.a6,d,d,q,d),r,e.z,""+e.f+" / "+e.e+" enabled","Optimization")}}
A.qC.prototype={
$0(){return A.a([],t.eB)},
$S:112}
A.qD.prototype={
$0(){var s=this.a.x
return s==null?null:s.$1(!0)},
$S:0}
A.qE.prototype={
$0(){var s=this.a.x
return s==null?null:s.$1(!1)},
$S:0}
A.qF.prototype={
$1(a){return t.j.a(a).d},
$S:6}
A.m9.prototype={
l(a){var s,r=this,q=null,p=r.d,o=p.d,n=o?"inset 3px 0 0 var(--success)":"none",m=t.N
m=A.B(A.j(["display","flex","flex-direction","column","gap","0.75rem","padding","0.85rem 0.95rem","overflow","hidden","border-radius","0.5rem","box-shadow",n],m,m))
n=t.i
p=A.a([A.H(A.a([new A.k(p.b,q)],n),q,q,q,B.l3),A.H(A.a([new A.k(p.e,q)],n),q,q,q,B.lA)],n)
s=r.e
p=A.a([new A.c(q,q,B.bC,q,q,p,q),A.nR(s,q,s?q:new A.uD(r),o)],n)
return A.jp("0.5rem",new A.c(q,q,m,q,q,A.a([new A.c(q,q,B.lW,q,q,p,q),new A.c(q,q,B.lt,q,q,A.a([A.yu(s,"Configure",s?q:new A.uE(r),B.h)],n),q)],n),q),!0,"0")}}
A.uD.prototype={
$1(a){var s=this.a,r=s.f
return r==null?null:r.$2(s.d.a,a)},
$S:9}
A.uE.prototype={
$0(){var s=this.a,r=s.r
return r==null?null:r.$1(s.d.a)},
$S:0}
A.eZ.prototype={
U(){return new A.mw()}}
A.mw.prototype={
a5(){var s,r,q=this
q.ar()
s=q.c.H(t.A)
r=s==null?null:s.d
if(r!=null&&!q.f){q.f=!0
q.d=r
s=new A.eB(r,!1,new A.vP(q),new A.vQ(),B.R)
q.e=s
s.T()}},
l(a0){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e=this,d=null,c="Optimization",b="Runtime feature control",a="Feature Control"
if(e.d==null){s=t.i
return A.a2(d,A.a([A.J(new A.c(d,d,B.i,d,d,A.a([new A.k("Feature control requires a live connection.",d)],s),d),d,d,!1,a,d)],s),d,b,c)}r=e.e
if(r.f&&J.eq(r.e)){s=t.i
return A.a2(d,A.a([A.J(new A.c(d,d,B.i,d,d,A.a([new A.k("Loading features...",d)],s),d),d,d,!1,a,d)],s),d,b,c)}s=a0.H(t.U)
q=s==null?d:s.d
p=q!=null&&!B.b.v(q.b,"op:execute")
if(e.r!=null){o=J.cq(r.e,new A.vJ(e))
n=o>=0?J.be(r.e,o):d}else n=d
m=e.r
s=r.e
l=J.b4(s)
k=J.zE(r.e,new A.vK()).gn(0)
j=p?d:r.gfe()
i=p?d:r.gis(r)
h=p?d:new A.vL(e)
g=e.r
f=p||m==null?d:new A.vM(r,m)
return new A.bK(A.a([new A.kv(s,l,k,j,h,i,p,new A.cO(q,d),d),new A.jB(g!=null,n,f,new A.vN(e),p,d)],t.i),d)}}
A.vP.prototype={
$0(){return this.a.t(new A.vO())},
$S:0}
A.vO.prototype={
$0(){},
$S:0}
A.vQ.prototype={
$1(a){return A.cu("Update failed",J.aF(a))},
$S:10}
A.vJ.prototype={
$1(a){return t.j.a(a).a===this.a.r},
$S:6}
A.vK.prototype={
$1(a){return t.j.a(a).d},
$S:6}
A.vL.prototype={
$1(a){var s=this.a
return s.t(new A.vI(s,a))},
$S:2}
A.vI.prototype={
$0(){return this.a.r=this.b},
$S:0}
A.vM.prototype={
$2(a,b){return this.a.bx(this.b,a,b)},
$S:36}
A.vN.prototype={
$0(){var s=this.a
return s.t(new A.vH(s))},
$S:0}
A.vH.prototype={
$0(){return this.a.r=null},
$S:0}
A.kx.prototype={
l(a){var s,r,q,p,o,n,m,l,k,j,i,h,g=null,f=a.H(t.V),e=f==null
if(e)s=g
else{r=f.d
s=r==null?g:r.a.j(0,"ticks-per-second")}if(e)q=g
else{r=f.d
q=r==null?g:r.a.j(0,"incident-score")}if(e)p=g
else{r=f.d
p=r==null?g:r.a.j(0,"tick-time")}if(e)o=g
else{r=f.d
o=r==null?g:r.a.j(0,"players")}if(e)n=g
else{r=f.d
n=r==null?g:r.a.j(0,"entities")}if(e)m=g
else{r=f.d
m=r==null?g:r.a.j(0,"chunks")}if(e)l=g
else{r=f.d
l=r==null?g:r.a.j(0,"memory-used")}if(e)k=g
else{e=f.d
k=e==null?g:e.a.j(0,"gc-time-percent")}e=s==null
r=e?g:s.d
if(r==null)r=0
r=A.cb(e?g:s.e,!0,"TPS",20,B.ak,r)
e=q==null
j=e?g:q.d
if(j==null)j=0
j=A.cb(e?g:q.e,!1,"Incident Score",100,B.S,j)
e=p==null
i=e?g:p.d
if(i==null)i=0
h=t.i
return A.a2(g,A.a([A.J(new A.c(g,g,B.l5,g,g,A.a([r,j,A.cb(e?g:p.e,!1,"Tick Time",50,B.jn,i)],h),g),g,g,!1,"Vitals",g),A.aK(A.a([A.Y("Players",o),A.Y("Entities",n),A.Y("Chunks",m),A.Y("Memory Used",l),A.Y("GC Time",k)],h),"220px"),new A.mm(q,g)],h),g,"Live server health and key runtime telemetry","Overview")}}
A.mm.prototype={
l(a){var s,r,q,p,o,n,m=null,l=this.d,k=l==null,j=k?m:l.w
if(j==null)j=B.j
s=k?m:l.d
if(s==null)s=0
l=A.yD(s,B.S).a
switch(l){case 0:k=B.C
break
case 1:k=B.t
break
case 2:k=B.K
break
default:k=m}switch(l){case 0:l="Normal"
break
case 1:l="Elevated"
break
case 2:l="Critical"
break
default:l=m}r=A.iW(k)
q=t.i
if(j.length===0)p=new A.c(m,m,B.ks,m,m,A.a([new A.k("No incident history yet",m)],q),m)
else{q=A.a([],q)
for(o=j.length,n=0;n<j.length;j.length===o||(0,A.I)(j),++n)q.push(new A.mk(j[n],100,r,m))
p=new A.c(m,m,B.lc,m,m,q,m)}return new A.kj("Incident Pressure",B.e.Z(s,0),A.iV(l,k),p,m)}}
A.mk.prototype={
l(a){var s=null,r=this.e,q=this.f,p=t.N
return new A.c(s,s,A.B(A.j(["flex","1","min-width","3px","height",""+B.c.a3(B.e.ac((r>0?B.e.a3(this.d/r,0,1):0)*100),5,100)+"%","background","linear-gradient(to top, "+q+", color-mix(in srgb, "+q+" 55%, transparent))","border-radius","2px","transition","height 200ms ease"],p,p)),s,s,B.n,s)}}
A.kB.prototype={
l(a){var s,r,q,p,o,n,m,l,k,j,i=null,h=a.H(t.V),g=h==null
if(g)s=i
else{r=h.d
s=r==null?i:r.a.j(0,"tick-time")}if(g)q=i
else{r=h.d
q=r==null?i:r.a.j(0,"tick-ms-p50")}if(g)p=i
else{r=h.d
p=r==null?i:r.a.j(0,"tick-ms-p95")}if(g)o=i
else{r=h.d
o=r==null?i:r.a.j(0,"tick-ms-p99")}if(g)n=i
else{r=h.d
n=r==null?i:r.a.j(0,"tick-spike-rate")}if(g)m=i
else{r=h.d
m=r==null?i:r.a.j(0,"top-world-mspt")}if(g)l=i
else{g=h.d
l=g==null?i:g.a.j(0,"top-chunk-cost")}g=s==null?i:s.w
if(g==null)g=B.j
r=t.y
g=A.a([new A.A("Tick Time",g)],r)
if(q!=null)g.push(new A.A("p50",q.w))
if(p!=null)g.push(new A.A("p95",p.w))
if(o!=null)g.push(new A.A("p99",o.w))
k=n==null?i:n.w
j=A.a([new A.A("Spike Rate",k==null?B.j:k)],r)
r=t.i
return A.a2(i,A.a([A.J(new A.ay(g,180,i),i,i,!1,"Tick Duration",i),A.J(new A.ay(j,80,i),i,i,!1,"Tick Spike Rate",i),new A.c(i,i,B.bI,i,i,A.a([A.Y("Top World MSPT",m),A.Y("Top Chunk Cost",l)],r),i)],r),i,"Tick timing and load hotspots","Performance")}}
A.dp.prototype={
U(){var s=t.N
return new A.iA(A.t(s,t.dK),A.cJ(s),A.t(s,s),A.t(s,s))}}
A.iA.prototype={
a5(){var s,r,q,p,o,n,m,l,k,j,i=this
i.ar()
if(i.d)return
i.d=!0
s=i.c
s.toString
r=A.cA(s)
if(r==null)return
s=r.f
s===$&&A.S()
s=s.d
s===$&&A.S()
i.e=i.aj(s.a)
i.f=i.aj(s.b)
i.r=i.aj(s.c)
i.w=i.aj(s.d)
i.x=i.aj(s.e)
i.y=i.aj(s.f)
i.z=i.aj(s.r)
s=r.e
s===$&&A.S()
q=A.al(s.d,t.C)
p=q.length
o=i.as
n=i.at
m=i.ax
l=0
for(;l<p;++l){k=q[l]
j=k.a
n.i(0,j,k.b)
m.i(0,j,"")
if(o.m(0,j))i.c5(s,j)}},
aj(a){if(a===(a<0?Math.ceil(a):Math.floor(a)))return B.c.k(B.e.bv(a))
return B.e.k(a)},
c5(a,b){return this.jX(a,b)},
jX(a,b){var s=0,r=A.Q(t.H),q,p=2,o=[],n=this,m,l,k,j
var $async$c5=A.R(function(c,d){if(c===1){o.push(d)
s=p}for(;;)switch(s){case 0:k=a.i7(b)
if(k==null){s=1
break}p=4
s=7
return A.G(k.cv(),$async$c5)
case 7:m=d
if(n.c==null){s=1
break}n.t(new A.w2(n,b,m))
p=2
s=6
break
case 4:p=3
j=o.pop()
s=1
break
s=6
break
case 3:s=2
break
case 6:case 1:return A.O(q,r)
case 2:return A.N(o.at(-1),r)}})
return A.P($async$c5,r)},
l0(a){var s=a.f
s===$&&A.S()
s.si9(B.F)
this.t(new A.w5(this,B.F))},
jQ(a,b){var s,r=this.at.j(0,b)
if(r==null)r=""
if(r.length===0)return
s=a.e
s===$&&A.S()
s.n8(b,r)
this.t(new A.w1())},
kW(a,b){a.n7(b)
this.t(new A.w3(this,b))},
jB(a){var s,r=this
if(!r.ch){s=a.e
s===$&&A.S()
s=A.al(s.d,t.C).length!==0}else s=!1
if(s){r.t(new A.vZ(r))
return}a.hB()
r.t(new A.w_(r))
A.dc("Saved fleet cleared",null)},
jb(a,b){var s,r,q=this.ax.j(0,b),p=B.a.aG(q==null?"":q)
if(p.length===0)return
q=a.r
q===$&&A.S()
s=q.fa(b)
r=A.a(s.slice(0),A.F(s))
if(!B.b.v(r,p))B.b.m(r,p)
q.fk(b,r)
this.t(new A.vX(this,b))},
kX(a,b,c){var s,r,q=a.r
q===$&&A.S()
s=q.fa(b)
r=A.a(s.slice(0),A.F(s))
B.b.J(r,c)
q.fk(b,r)
this.t(new A.w4())},
jV(a){var s,r,q,p,o,n,m=a.e
m===$&&A.S()
s=A.al(m.d,t.C)
m=s.length
if(m===0){A.cu("Nothing to export","No servers configured.")
return}r=A.H3(s)
q=v.G
p=A.p(new q.Blob(A.a([r],t.hf),{type:"application/json"}))
o=A.r(q.URL.createObjectURL(p))
n=A.p(A.p(q.document).createElement("a"))
n.href=o
n.download="reactor-fleet-"+m+"-servers.json"
n.click()
q.URL.revokeObjectURL(o)},
ly(){var s=this
if(s.CW)return
s.CW=!0
s.t(new A.wE(s))
A.Ib(new A.wF(s))},
jI(a){var s,r,q=this.cx
if(q==null||q.c!=null)return
s=q.a
a.mA(s)
this.t(new A.w0(this,a))
s=s.length
r=s===1?"":"s"
A.dc("Fleet imported",""+s+" server"+r+" loaded.")},
jt(){this.t(new A.vY(this))},
l(a){var s,r,q=null,p="Settings",o="Alert thresholds and server tags",n=A.cA(a)
if(n==null)return A.a2(q,A.a([A.ct("No fleet has been initialized.","Fleet unavailable")],t.i),q,o,p)
s=n.e
s===$&&A.S()
r=A.al(s.d,t.C)
return A.a2(q,A.a([this.l3(r),this.lt(n),this.ld(n,r)],t.i),q,o,p)},
l3(a){var s,r,q,p,o,n,m,l,k,j=null
t.jO.a(a)
s=t.i
r=A.a([],s)
q=a.length
if(q===0)r.push(new A.c(j,j,B.bE,j,j,A.a([new A.k("No servers configured.",j)],s),j))
for(p=this.Q,o=0;o<q;++o){n=a[o]
m=n.a
l=p.j(0,m)
k=p.K(m)
m=A.a([new A.c(j,j,B.kV,j,j,A.a([new A.k(n.b,j)],s),j)],s)
if(k&&l!=null)m.push(new A.cO(l,j))
else m.push(new A.k("\u2014",j))
r.push(new A.c(j,j,B.bH,j,j,m,j))}return A.J(new A.c(j,j,B.a4,j,j,r,j),j,j,!1,"Account / Roles",j)},
lt(a){var s=this,r=null,q=t.i
return A.J(new A.c(r,r,B.a4,r,r,A.a([s.bn("TPS Warn",new A.wt(s),s.e),s.bn("TPS Critical",new A.wu(s),s.f),s.bn("MSPT Warn",new A.wv(s),s.r),s.bn("Incident Score Warn",new A.ww(s),s.w),s.bn("GC Percent Warn",new A.wx(s),s.x),s.bn("Ping P95 Warn",new A.wy(s),s.y),s.bn("Memory Pressure Warn",new A.wz(s),s.z),new A.c(r,r,B.lT,r,r,A.a([A.o4(!1,"Save thresholds",new A.wA(s,a),B.v),A.bf(!1,!1,"Reset to defaults",new A.wB(s,a),B.v)],q),r)],q),r),r,r,!1,"Alert Thresholds",r)},
bn(a,b,c){var s,r=null
t.eF.a(b)
s=t.i
return new A.c(r,r,B.bH,r,r,A.a([new A.c(r,r,B.ku,r,r,A.a([new A.k(a,r)],s),r),new A.c(r,r,B.kq,r,r,A.a([A.cW(!1,r,!0,r,r,b,r,r,B.V,c)],s),r)],s),r)},
ld(a,b){var s,r,q,p,o,n,m,l,k,j,i=this,h=null
t.jO.a(b)
s=i.ch?"Confirm clear all":"Clear all"
r=b.length
q=r===0
s=A.yt(q,s,q?h:new A.wh(i,a),B.h)
p=t.i
o=A.a([new A.c(h,h,B.aq,h,h,A.a([A.bf(!1,!1,"Export connections",new A.wi(i,a),B.h),A.bf(!1,!1,"Import connections",new A.wj(i),B.h)],p),h),new A.c(h,h,B.at,h,h,A.a([new A.k("Export files contain bearer tokens and credentials. Store them securely.",h)],p),h)],p)
n=i.cy
if(n!=null)o.push(new A.c(h,h,B.lq,h,h,A.a([new A.k("Import failed: "+n,h)],p),h))
n=i.cx
if(n!=null&&n.c==null){m=r===1?"":"s"
l=n.a.length
n=n.b
if(n>0){k=n===1?"entry":"entries"
k=" ("+n+" malformed "+k+" skipped)"
n=k}else n=""
o.push(A.zG("Import",!0,"This will replace your "+r+" current server"+m+" with "+l+" from the file"+n+".",i.gjs(),new A.wk(i,a),"Replace fleet?"))}if(q)o.push(new A.c(h,h,B.bE,h,h,A.a([new A.k("No servers configured.",h)],p),h))
for(j=0;j<r;++j)o.push(i.la(a,b[j]))
return A.J(new A.c(h,h,B.m2,h,h,o,h),h,h,!1,"Saved Servers",s)},
la(a,b){var s,r,q,p,o,n,m,l,k=this,j=null,i=a.r
i===$&&A.S()
s=b.a
r=i.fa(s)
q=k.ax.j(0,s)
if(q==null)q=""
p=k.at.j(0,s)
if(p==null)p=b.b
i=b.b
o=t.i
n=A.a([A.lr(i),new A.c(j,j,B.a6,j,j,A.a([new A.c(j,j,B.bz,j,j,A.a([A.cW(!1,j,!0,j,j,new A.wa(k,b),j,"Server label",B.V,p)],o),j),A.bf(!1,!1,"Rename",new A.wb(k,a,b),B.h),A.yt(!1,"Remove",new A.wc(k,b),B.h)],o),j)],o)
if(k.ay===s)n.push(A.zG("Remove",!0,"This will disconnect and remove the server from your fleet.",new A.wd(k),new A.we(k,a,b),"Remove "+i+"?"))
i=A.a([],o)
for(m=r.length,l=0;l<m;++l)i.push(k.lq(a,s,r[l]))
i.push(new A.c(j,j,B.m7,j,j,A.a([A.cW(!1,j,!0,j,j,new A.wf(k,b),j,"Add tag",B.V,q)],o),j))
i.push(A.bf(!1,!1,"Add tag",new A.wg(k,a,b),B.h))
n.push(new A.c(j,j,B.kN,j,j,i,j))
return A.ez(new A.c(j,j,B.lk,j,j,n,j),!0)},
lq(a,b,c){var s=null
return new A.c(s,s,B.lf,s,s,A.a([A.A6(c),A.yu(!1,"\xd7",new A.wl(this,a,b,c),B.h)],t.i),s)}}
A.w2.prototype={
$0(){var s=this.c
this.a.Q.i(0,this.b,s)
return s},
$S:0}
A.w5.prototype={
$0(){var s=this.a,r=this.b
s.e=s.aj(r.a)
s.f=s.aj(r.b)
s.r=s.aj(r.c)
s.w=s.aj(r.d)
s.x=s.aj(r.e)
s.y=s.aj(r.f)
s.z=s.aj(r.r)},
$S:0}
A.w1.prototype={
$0(){},
$S:0}
A.w3.prototype={
$0(){var s,r=this.a
r.ay=null
r.ch=!1
s=this.b
r.at.J(0,s)
r.ax.J(0,s)
r.Q.J(0,s)
r.as.J(0,s)},
$S:0}
A.vZ.prototype={
$0(){return this.a.ch=!0},
$S:0}
A.w_.prototype={
$0(){var s=this.a
s.ch=!1
s.ay=null
s.at.O(0)
s.ax.O(0)
s.Q.O(0)
s.as.O(0)},
$S:0}
A.vX.prototype={
$0(){this.a.ax.i(0,this.b,"")},
$S:0}
A.w4.prototype={
$0(){},
$S:0}
A.wE.prototype={
$0(){var s=this.a
s.cy=s.cx=null},
$S:0}
A.wF.prototype={
$1(a){var s,r=this.a
if(r.c==null){r.CW=!1
return}s=A.I8(a)
if(s.c!=null){r.t(new A.wC(r,s))
return}r.t(new A.wD(r,s))},
$S:2}
A.wC.prototype={
$0(){var s=this.a
s.CW=!1
s.cy=this.b.c
s.cx=null},
$S:0}
A.wD.prototype={
$0(){var s=this.a
s.CW=!1
s.cx=this.b
s.cy=null},
$S:0}
A.w0.prototype={
$0(){var s,r,q,p,o,n,m,l,k,j=this.a
j.cy=j.cx=null
s=j.at
s.O(0)
r=j.ax
r.O(0)
j.Q.O(0)
q=j.as
q.O(0)
j.ay=null
j.ch=!1
p=this.b.e
p===$&&A.S()
o=A.al(p.d,t.C)
n=o.length
m=0
for(;m<n;++m){l=o[m]
k=l.a
s.i(0,k,l.b)
r.i(0,k,"")
if(q.m(0,k))j.c5(p,k)}},
$S:0}
A.vY.prototype={
$0(){var s=this.a
s.cy=s.cx=null},
$S:0}
A.wt.prototype={
$1(a){var s=this.a
return s.t(new A.ws(s,a))},
$S:2}
A.ws.prototype={
$0(){return this.a.e=this.b},
$S:0}
A.wu.prototype={
$1(a){var s=this.a
return s.t(new A.wr(s,a))},
$S:2}
A.wr.prototype={
$0(){return this.a.f=this.b},
$S:0}
A.wv.prototype={
$1(a){var s=this.a
return s.t(new A.wq(s,a))},
$S:2}
A.wq.prototype={
$0(){return this.a.r=this.b},
$S:0}
A.ww.prototype={
$1(a){var s=this.a
return s.t(new A.wp(s,a))},
$S:2}
A.wp.prototype={
$0(){return this.a.w=this.b},
$S:0}
A.wx.prototype={
$1(a){var s=this.a
return s.t(new A.wo(s,a))},
$S:2}
A.wo.prototype={
$0(){return this.a.x=this.b},
$S:0}
A.wy.prototype={
$1(a){var s=this.a
return s.t(new A.wn(s,a))},
$S:2}
A.wn.prototype={
$0(){return this.a.y=this.b},
$S:0}
A.wz.prototype={
$1(a){var s=this.a
return s.t(new A.wm(s,a))},
$S:2}
A.wm.prototype={
$0(){return this.a.z=this.b},
$S:0}
A.wA.prototype={
$0(){var s,r,q,p,o,n,m=this.a,l=A.bZ(m.e)
if(l==null)l=18
s=A.bZ(m.f)
if(s==null)s=10
r=A.bZ(m.r)
if(r==null)r=50
q=A.bZ(m.w)
if(q==null)q=50
p=A.bZ(m.x)
if(p==null)p=15
o=A.bZ(m.y)
if(o==null)o=200
m=A.bZ(m.z)
if(m==null)m=90
n=this.b.f
n===$&&A.S()
n.si9(new A.fL(l,s,r,q,p,o,m))
A.dc("Thresholds saved",null)
return null},
$S:0}
A.wB.prototype={
$0(){return this.a.l0(this.b)},
$S:0}
A.wh.prototype={
$0(){return this.a.jB(this.b)},
$S:0}
A.wi.prototype={
$0(){return this.a.jV(this.b)},
$S:0}
A.wj.prototype={
$0(){return this.a.ly()},
$S:0}
A.wk.prototype={
$0(){return this.a.jI(this.b)},
$S:0}
A.wa.prototype={
$1(a){var s=this.a
return s.t(new A.w9(s,this.b,a))},
$S:2}
A.w9.prototype={
$0(){var s=this.c
this.a.at.i(0,this.b.a,s)
return s},
$S:0}
A.wb.prototype={
$0(){return this.a.jQ(this.b,this.c.a)},
$S:0}
A.wc.prototype={
$0(){var s=this.a
return s.t(new A.w8(s,this.b))},
$S:0}
A.w8.prototype={
$0(){return this.a.ay=this.b.a},
$S:0}
A.we.prototype={
$0(){return this.a.kW(this.b,this.c.a)},
$S:0}
A.wd.prototype={
$0(){var s=this.a
return s.t(new A.w7(s))},
$S:0}
A.w7.prototype={
$0(){return this.a.ay=null},
$S:0}
A.wf.prototype={
$1(a){var s=this.a
return s.t(new A.w6(s,this.b,a))},
$S:2}
A.w6.prototype={
$0(){var s=this.c
this.a.ax.i(0,this.b.a,s)
return s},
$S:0}
A.wg.prototype={
$0(){return this.a.jb(this.b,this.c.a)},
$S:0}
A.wl.prototype={
$0(){var s=this
return s.a.kX(s.b,s.c,s.d)},
$S:0}
A.lz.prototype={
l(a){var s,r,q,p,o,n,m,l,k,j,i,h=this,g=null,f=t.i,e=A.a([],f)
for(s=J.aE(h.d),r=h.r,q=t.kk;s.p();){p=s.gu()
o=A.lr(p.b)
n=A.B8(p.e,B.bw,B.aM)
m=p.d
o=A.a([o,n,A.nR(r,g,r?g:new A.tc(h,p),m)],f)
n=p.f
m=n.length
if(m!==0){l=A.a([],f)
for(k=n.length,j=0;j<n.length;n.length===k||(0,A.I)(n),++j){i=n[j]
l.push(new A.dQ(i,r?g:new A.td(h,p,i),r,g))}o.push(new A.j2(A.a([new A.dF("Configure ("+m+")",new A.h_(l,8,g))],q),g))}e.push(A.ez(new A.c(g,g,B.a3,g,g,o,g),!0))}return A.bJ(e,12)}}
A.tc.prototype={
$1(a){var s=this.a.e
return s==null?null:s.$2(this.b.a,a)},
$S:9}
A.td.prototype={
$1(a){var s=this.a.f
return s==null?null:s.$3(this.b.a,this.c.a,a)},
$S:14}
A.fg.prototype={
U(){return new A.mM()}}
A.mM.prototype={
a5(){var s,r,q=this
q.ar()
s=q.c.H(t.A)
r=s==null?null:s.d
if(r!=null&&!q.f){q.f=!0
q.d=r
s=new A.eB(r,!0,new A.wO(q),new A.wP(),B.R)
q.e=s
s.T()}},
l(a){var s,r,q,p,o,n=null,m="Tweaks",l="Fine-grained runtime tweaks",k="Tweak Control"
if(this.d==null){s=t.i
return A.a2(n,A.a([A.J(new A.c(n,n,B.i,n,n,A.a([new A.k("Tweak control requires a live connection.",n)],s),n),n,n,!1,k,n)],s),n,l,m)}r=this.e
if(r.f&&J.eq(r.e)){s=t.i
return A.a2(n,A.a([A.J(new A.c(n,n,B.i,n,n,A.a([new A.k("Loading tweaks...",n)],s),n),n,n,!1,k,n)],s),n,l,m)}s=a.H(t.U)
q=s==null?n:s.d
p=q!=null&&!B.b.v(q.b,"op:execute")
s=r.e
o=p?n:r.gfe()
return A.a2(n,A.a([new A.lz(s,o,p?n:r.giA(),p,n)],t.i),new A.cO(q,n),l,m)}}
A.wO.prototype={
$0(){return this.a.t(new A.wN())},
$S:0}
A.wN.prototype={
$0(){},
$S:0}
A.wP.prototype={
$1(a){return A.cu("Update failed",J.aF(a))},
$S:10}
A.lJ.prototype={
kH(a){var s
switch(a.a){case 0:s=B.c0
break
case 1:s=B.c2
break
case 2:s=B.c3
break
default:s=null}return s},
l(a){var s,r,q,p,o,n,m,l,k=this,j="World Overrides",i="Per-world tick budgets",h=null,g=k.d,f=J.aT(g)
if(f.gL(g))return A.a2(h,A.a([A.ct("No worlds reported by the server.","No worlds")],t.i),k.r,i,j)
s=t.i
r=A.a([],s)
for(g=f.gC(g),f=k.f;g.p();){q=g.gu()
p=A.lr(q.a)
o=k.kH(q.b)
n=B.e.k(q.c)
n=A.cW(f,h,!1,h,"Budget (ms)",f?h:new A.ts(k,q),h,h,B.W,n)
m=B.e.k(q.d)
m=A.cW(f,h,!1,h,"Panic (ms)",f?h:new A.tt(k,q),h,h,B.W,m)
l=B.e.k(q.e)
r.push(A.ez(new A.c(h,h,B.a3,h,h,A.a([p,o,n,m,A.cW(f,h,!1,h,"Release (ms)",f?h:new A.tu(k,q),h,h,B.W,l)],s),h),!0))}return A.a2(h,r,k.r,i,j)}}
A.ts.prototype={
$1(a){var s,r=A.bZ(a.aG(0))
if(r!=null){s=this.a.e
if(s!=null)s.$2$budgetMs(this.b.a,r)}},
$S:2}
A.tt.prototype={
$1(a){var s,r=A.bZ(a.aG(0))
if(r!=null){s=this.a.e
if(s!=null)s.$2$panicMs(this.b.a,r)}},
$S:2}
A.tu.prototype={
$1(a){var s,r=A.bZ(a.aG(0))
if(r!=null){s=this.a.e
if(s!=null)s.$2$releaseMs(this.b.a,r)}},
$S:2}
A.fi.prototype={
U(){return new A.mR()}}
A.mR.prototype={
a5(){var s,r,q=this
q.ar()
s=q.c.H(t.A)
r=s==null?null:s.d
if(r!=null&&!q.f){q.f=!0
q.d=r
s=new A.lI(r,new A.x6(q),new A.x7(),B.dx)
q.e=s
s.T()}},
l(a){var s,r,q,p,o,n=null,m="World Overrides",l="Per-world tick budgets",k="Per-World Overrides"
if(this.d==null){s=t.i
return A.a2(n,A.a([A.J(new A.c(n,n,B.i,n,n,A.a([new A.k("Per-world overrides require a live connection.",n)],s),n),n,n,!1,k,n)],s),n,l,m)}r=this.e
if(r.e&&J.eq(r.d)){s=t.i
return A.a2(n,A.a([A.J(new A.c(n,n,B.i,n,n,A.a([new A.k("Loading worlds...",n)],s),n),n,n,!1,k,n)],s),n,l,m)}s=a.H(t.U)
q=s==null?n:s.d
p=q!=null&&!B.b.v(q.b,"op:execute")
s=r.d
o=p?n:r.git()
return new A.lJ(s,o,p,new A.cO(q,n),n)}}
A.x6.prototype={
$0(){return this.a.t(new A.x5())},
$S:0}
A.x5.prototype={
$0(){},
$S:0}
A.x7.prototype={
$1(a){return A.cu("Update failed",J.aF(a))},
$S:10}
A.lK.prototype={
l(a){var s,r,q,p,o,n=null,m="Top World MSPT",l=a.H(t.V),k=l==null
if(k)s=n
else{r=l.d
s=r==null?n:r.a.j(0,"top-world-mspt")}if(k)q=n
else{r=l.d
q=r==null?n:r.a.j(0,"per-world-tick-time")}if(k)p=n
else{k=l.d
p=k==null?n:k.a.j(0,"top-chunk-cost")}k=s==null?n:s.w
if(k==null)k=B.j
r=q==null?n:q.w
if(r==null)r=B.j
o=t.i
return A.a2(n,A.a([A.J(new A.ay(A.a([new A.A(m,k),new A.A("Per-World Tick",r)],t.y),160,n),n,n,!1,m,n),A.aK(A.a([A.Y(m,s),A.Y("Per-World Tick Time",q),A.Y("Top Chunk Cost",p)],o),"220px"),A.J(A.ct("Open the World Overrides screen to view per-world NORMAL/PRESSURE/PANIC state and edit tick budgets.","Per-world tick budgets moved to World Overrides"),n,n,!1,"Per-World Breakdown",n)],o),n,"Per-world performance","Worlds")}}
A.dW.prototype={
E(){return"RelayPath."+this.b}}
A.jX.prototype={
cW(){return this.l1()},
l1(){var s=0,r=A.Q(t.lF),q,p=this,o,n,m,l,k,j,i,h,g
var $async$cW=A.R(function(a,b){if(a===1)return A.N(b,r)
for(;;)switch(s){case 0:h={}
g=p.d
if(g!==B.al){o=g===B.bs?p.a:p.b
if(o!=null){q=o
s=1
break}}g=new A.a_($.a0,t.h_)
n=new A.c3(g,t.am)
m=h.a=0
h.b=null
h.c=!1
l=p.a
k=l!=null
if(k)m=h.a=1
j=p.b
i=j!=null
if(i)h.a=m+1
h=new A.pt(h,p,n,new A.ps(h,n))
if(k)h.$2(B.bs,l)
if(i)h.$2(B.jD,j)
q=g
s=1
break
case 1:return A.O(q,r)}})
return A.P($async$cW,r)},
aK(){var s=0,r=A.Q(t.kL),q,p=this
var $async$aK=A.R(function(a,b){if(a===1)return A.N(b,r)
for(;;)switch(s){case 0:s=3
return A.G(p.cW(),$async$aK)
case 3:q=b.aK()
s=1
break
case 1:return A.O(q,r)}})
return A.P($async$aK,r)},
aN(){var s=0,r=A.Q(t.c),q,p=2,o=[],n=this,m,l,k,j
var $async$aN=A.R(function(a,b){if(a===1){o.push(b)
s=p}for(;;)switch(s){case 0:s=3
return A.G(n.cW(),$async$aN)
case 3:k=b
p=5
s=8
return A.G(k.aN(),$async$aN)
case 8:m=b
q=m
s=1
break
p=2
s=7
break
case 5:p=4
j=o.pop()
if(A.a1(j) instanceof A.b8){n.d=B.al
throw j}else throw j
s=7
break
case 4:s=2
break
case 7:case 1:return A.O(q,r)
case 2:return A.N(o.at(-1),r)}})
return A.P($async$aN,r)},
$ipS:1,
$idi:1}
A.ps.prototype={
$0(){var s,r=this.a
if(r.a<=0&&(this.b.a.a&30)===0){s=this.b
if(r.c)s.bI(B.bk)
else{r=r.b
s.bI(r==null?B.iN:r)}}},
$S:0}
A.pt.prototype={
ij(a,b){var s=0,r=A.Q(t.H),q=1,p=[],o=this,n,m,l,k,j,i
var $async$$2=A.R(function(c,d){if(c===1){p.push(d)
s=q}for(;;)switch(s){case 0:q=3
s=6
return A.G(b.aK(),$async$$2)
case 6:n=d
l=o.a;--l.a
k=o.b
if(A.EH(n.d,k.c)){l=o.c
if((l.a.a&30)===0){k.d=a
l.ba(b)}}else{l.c=!0
l.b=B.bk
o.d.$0()}q=1
s=5
break
case 3:q=2
i=p.pop()
m=A.a1(i)
l=o.a;--l.a
l.b=m
o.d.$0()
s=5
break
case 2:s=1
break
case 5:return A.O(null,r)
case 1:return A.N(p.at(-1),r)}})
return A.P($async$$2,r)},
$2(a,b){return this.ij(a,b)},
$S:114}
A.qx.prototype={
hW(a){var s="reactor.counter."+a,r=this.a,q=r.cp(s),p=q!=null?A.hH(q,null):null,o=p==null?this.b.$0():p+1
r.bU(s,B.c.k(o))
return o}}
A.qy.prototype={
$0(){return Date.now()},
$S:18}
A.ci.prototype={
ge_(){var s=this.a
return(s.f?"https":"http")+"://"+s.c+":"+s.d+"/api/v1"},
af(a){var s=0,r=A.Q(t.cD),q,p=2,o=[],n=this,m,l,k,j,i,h,g,f
var $async$af=A.R(function(b,c){if(b===1){o.push(c)
s=p}for(;;)switch(s){case 0:g=A.bN(n.ge_()+a)
p=4
i=t.N
i=A.j(["Authorization","Bearer "+n.a.e,"Content-Type","application/json"],i,i)
s=7
return A.G(n.b.l4("GET",t.R.a(g),t.t.a(i)).fb(B.O),$async$af)
case 7:m=c
if(m.b===401)throw A.d(B.aj)
q=m
s=1
break
p=2
s=6
break
case 4:p=3
f=o.pop()
i=A.a1(f)
if(i instanceof A.dm)throw f
else if(i instanceof A.c8){l=i
throw A.d(A.bM(l.a))}else if(i instanceof A.e5){k=i
i=k.a
throw A.d(A.bM(i))}else if(t.mA.b(i)){j=i
throw A.d(A.bM(J.aF(j)))}else throw f
s=6
break
case 3:s=2
break
case 6:case 1:return A.O(q,r)
case 2:return A.N(o.at(-1),r)}})
return A.P($async$af,r)},
bl(a){var s,r,q
try{r=t.P.a(B.k.aJ(a,null))
return r}catch(q){r=A.a1(q)
if(t.lW.b(r)){s=r
throw A.d(A.bM(s.geZ()))}else throw q}},
b7(a){var s=this.bl(a).j(0,"data")
if(!t.P.b(s))throw A.d(B.iM)
return s},
b8(a){var s,r,q,p,o,n="Request failed"
try{s=B.k.aJ(a,null)
p=t.P
if(p.b(s)){r=s.j(0,"error")
if(p.b(r)){q=r.j(0,"message")
if(typeof q=="string")return q}}return n}catch(o){return n}},
c6(a){var s=0,r=A.Q(t.iD),q,p=this,o,n,m
var $async$c6=A.R(function(b,c){if(b===1)return A.N(c,r)
for(;;)switch(s){case 0:s=3
return A.G(p.af(a),$async$c6)
case 3:n=c
m=p.bl(A.bt(A.bs(n.e)).a7(n.w)).j(0,"data")
if(!t._.b(m))throw A.d(B.iQ)
o=J.aU(m,new A.qM(),t.j)
o=A.x(o,o.$ti.h("z.E"))
q=o
s=1
break
case 1:return A.O(q,r)}})
return A.P($async$c6,r)},
aP(a,b){return this.kP(a,t.G.a(b))},
kP(a,b){var s=0,r=A.Q(t.P),q,p=2,o=[],n=this,m,l,k,j,i,h,g,f,e,d
var $async$aP=A.R(function(c,a0){if(c===1){o.push(a0)
s=p}for(;;)A:switch(s){case 0:h=A.bN(n.ge_()+a)
g=n.a
f=t.N
e=A.j(["Authorization","Bearer "+g.e,"Content-Type","application/json","X-React-Counter",B.c.k(n.c.hW(g.a))],f,f)
p=4
g=B.k.bb(b,null)
s=7
return A.G(n.b.bD("PUT",t.R.a(h),t.t.a(e),g,null).fb(B.O),$async$aP)
case 7:m=a0
switch(m.b){case 200:g=m
g=n.b7(A.bt(A.bs(g.e)).a7(g.w))
q=g
s=1
break A
case 401:throw A.d(B.aj)
case 403:g=A.AV(n.b8(m.gaI()))
throw A.d(g)
case 404:g=A.AW(n.b8(m.gaI()))
throw A.d(g)
case 409:g=A.AU(n.b8(m.gaI()))
throw A.d(g)
default:g=A.bM(n.b8(m.gaI()))
throw A.d(g)}p=2
s=6
break
case 4:p=3
d=o.pop()
g=A.a1(d)
if(g instanceof A.dm)throw d
else if(g instanceof A.f1)throw d
else if(g instanceof A.f2)throw d
else if(g instanceof A.f0)throw d
else if(g instanceof A.b8)throw d
else if(g instanceof A.c8){l=g
throw A.d(A.bM(l.a))}else if(g instanceof A.e5){k=g
g=k.a
throw A.d(A.bM(g))}else if(t.mA.b(g)){j=g
throw A.d(A.bM(J.aF(j)))}else throw d
s=6
break
case 3:s=2
break
case 6:case 1:return A.O(q,r)
case 2:return A.N(o.at(-1),r)}})
return A.P($async$aP,r)},
ca(a,b){return this.kE(a,t.G.a(b))},
kE(a,b){var s=0,r=A.Q(t.P),q,p=2,o=[],n=this,m,l,k,j,i,h,g,f,e,d
var $async$ca=A.R(function(c,a0){if(c===1){o.push(a0)
s=p}for(;;)A:switch(s){case 0:h=A.bN(n.ge_()+a)
g=n.a
f=t.N
e=A.j(["Authorization","Bearer "+g.e,"Content-Type","application/json","X-React-Counter",B.c.k(n.c.hW(g.a))],f,f)
p=4
g=B.k.bb(b,null)
s=7
return A.G(n.b.bD("POST",t.R.a(h),t.t.a(e),g,null).fb(B.O),$async$ca)
case 7:m=a0
switch(m.b){case 200:case 202:g=m
g=n.b7(A.bt(A.bs(g.e)).a7(g.w))
q=g
s=1
break A
case 401:throw A.d(B.aj)
case 403:g=A.AV(n.b8(m.gaI()))
throw A.d(g)
case 404:g=A.AW(n.b8(m.gaI()))
throw A.d(g)
case 409:g=A.AU(n.b8(m.gaI()))
throw A.d(g)
default:g=A.bM(n.b8(m.gaI()))
throw A.d(g)}p=2
s=6
break
case 4:p=3
d=o.pop()
g=A.a1(d)
if(g instanceof A.dm)throw d
else if(g instanceof A.f1)throw d
else if(g instanceof A.f2)throw d
else if(g instanceof A.f0)throw d
else if(g instanceof A.b8)throw d
else if(g instanceof A.c8){l=g
throw A.d(A.bM(l.a))}else if(g instanceof A.e5){k=g
g=k.a
throw A.d(A.bM(g))}else if(t.mA.b(g)){j=g
throw A.d(A.bM(J.aF(j)))}else throw d
s=6
break
case 3:s=2
break
case 6:case 1:return A.O(q,r)
case 2:return A.N(o.at(-1),r)}})
return A.P($async$ca,r)},
aK(){var s=0,r=A.Q(t.kL),q,p=this,o
var $async$aK=A.R(function(a,b){if(a===1)return A.N(b,r)
for(;;)switch(s){case 0:s=3
return A.G(p.af("/identity"),$async$aK)
case 3:o=b
q=A.Ed(p.b7(A.bt(A.bs(o.e)).a7(o.w)))
s=1
break
case 1:return A.O(q,r)}})
return A.P($async$aK,r)},
cv(){var s=0,r=A.Q(t.mo),q,p=this,o
var $async$cv=A.R(function(a,b){if(a===1)return A.N(b,r)
for(;;)switch(s){case 0:s=3
return A.G(p.af("/whoami"),$async$cv)
case 3:o=b
q=A.EJ(p.b7(A.bt(A.bs(o.e)).a7(o.w)))
s=1
break
case 1:return A.O(q,r)}})
return A.P($async$cv,r)},
aN(){var s=0,r=A.Q(t.c),q,p=this,o
var $async$aN=A.R(function(a,b){if(a===1)return A.N(b,r)
for(;;)switch(s){case 0:s=3
return A.G(p.af("/metrics"),$async$aN)
case 3:o=b
q=A.B1(p.bl(A.bt(A.bs(o.e)).a7(o.w)))
s=1
break
case 1:return A.O(q,r)}})
return A.P($async$aN,r)},
dq(){var s=0,r=A.Q(t.jP),q,p=this,o,n,m
var $async$dq=A.R(function(a,b){if(a===1)return A.N(b,r)
for(;;)switch(s){case 0:s=3
return A.G(p.af("/heatmaps"),$async$dq)
case 3:n=b
m=p.bl(A.bt(A.bs(n.e)).a7(n.w)).j(0,"data")
if(!t._.b(m))throw A.d(B.iL)
o=J.aU(m,new A.qP(),t.e_)
o=A.x(o,o.$ti.h("z.E"))
q=o
s=1
break
case 1:return A.O(q,r)}})
return A.P($async$dq,r)},
dn(a){var s=0,r=A.Q(t.lP),q,p=this,o,n,m,l,k
var $async$dn=A.R(function(b,c){if(b===1)return A.N(c,r)
for(;;)switch(s){case 0:l=t.N
k=A.t(l,l)
if(k.a===0)o=""
else{n=k.$ti.h("aC<1,2>")
o="?"+A.qs(new A.aC(k,n),n.h("b(m.E)").a(new A.qO()),n.h("m.E"),l).aA(0,"&")}s=3
return A.G(p.af("/heatmaps/"+a+o),$async$dn)
case 3:m=c
if(m.b===404)throw A.d(A.bM("Unknown heatmap: "+a))
q=A.E0(p.b7(A.bt(A.bs(m.e)).a7(m.w)))
s=1
break
case 1:return A.O(q,r)}})
return A.P($async$dn,r)},
cB(a,b){var s=0,r=A.Q(t.j),q,p=this,o
var $async$cB=A.R(function(c,d){if(c===1)return A.N(d,r)
for(;;)switch(s){case 0:o=A
s=3
return A.G(p.aP("/features/"+a,A.j(["enabled",b],t.N,t.X)),$async$cB)
case 3:q=o.jE(d)
s=1
break
case 1:return A.O(q,r)}})
return A.P($async$cB,r)},
cA(a,b){return this.iz(a,t.G.a(b))},
iz(a,b){var s=0,r=A.Q(t.j),q,p=this,o
var $async$cA=A.R(function(c,d){if(c===1)return A.N(d,r)
for(;;)switch(s){case 0:o=A
s=3
return A.G(p.aP("/features/"+a+"/config",b),$async$cA)
case 3:q=o.jE(d)
s=1
break
case 1:return A.O(q,r)}})
return A.P($async$cA,r)},
cE(a,b){var s=0,r=A.Q(t.j),q,p=this,o
var $async$cE=A.R(function(c,d){if(c===1)return A.N(d,r)
for(;;)switch(s){case 0:o=A
s=3
return A.G(p.aP("/tweaks/"+a,A.j(["enabled",b],t.N,t.X)),$async$cE)
case 3:q=o.jE(d)
s=1
break
case 1:return A.O(q,r)}})
return A.P($async$cE,r)},
cD(a,b){return this.iC(a,t.G.a(b))},
iC(a,b){var s=0,r=A.Q(t.j),q,p=this,o
var $async$cD=A.R(function(c,d){if(c===1)return A.N(d,r)
for(;;)switch(s){case 0:o=A
s=3
return A.G(p.aP("/tweaks/"+a+"/config",b),$async$cD)
case 3:q=o.jE(d)
s=1
break
case 1:return A.O(q,r)}})
return A.P($async$cD,r)},
dL(){var s=0,r=A.Q(t.et),q,p=this,o,n,m
var $async$dL=A.R(function(a,b){if(a===1)return A.N(b,r)
for(;;)switch(s){case 0:s=3
return A.G(p.af("/worlds"),$async$dL)
case 3:n=b
m=p.bl(A.bt(A.bs(n.e)).a7(n.w)).j(0,"data")
if(!t._.b(m))throw A.d(B.iO)
o=J.aU(m,new A.qR(),t.q)
o=A.x(o,o.$ti.h("z.E"))
q=o
s=1
break
case 1:return A.O(q,r)}})
return A.P($async$dL,r)},
cF(a,b,c,d){var s=0,r=A.Q(t.q),q,p=this,o,n
var $async$cF=A.R(function(e,f){if(e===1)return A.N(f,r)
for(;;)switch(s){case 0:o=A.t(t.N,t.X)
if(b!=null)o.i(0,"budgetMs",b)
if(c!=null)o.i(0,"panicMs",c)
if(d!=null)o.i(0,"releaseMs",d)
n=A
s=3
return A.G(p.aP("/worlds/"+a,o),$async$cF)
case 3:q=n.Bg(f)
s=1
break
case 1:return A.O(q,r)}})
return A.P($async$cF,r)},
d7(){var s=0,r=A.Q(t.fT),q,p=this,o,n,m
var $async$d7=A.R(function(a,b){if(a===1)return A.N(b,r)
for(;;)switch(s){case 0:s=3
return A.G(p.af("/actions"),$async$d7)
case 3:n=b
m=p.bl(A.bt(A.bs(n.e)).a7(n.w)).j(0,"data")
if(!t._.b(m))throw A.d(B.iP)
o=J.aU(m,new A.qN(),t.kS)
o=A.x(o,o.$ti.h("z.E"))
q=o
s=1
break
case 1:return A.O(q,r)}})
return A.P($async$d7,r)},
dk(a,b,c){return this.mm(a,b,t.G.a(c))},
mm(a,b,c){var s=0,r=A.Q(t.al),q,p=this,o
var $async$dk=A.R(function(d,e){if(d===1)return A.N(e,r)
for(;;)switch(s){case 0:s=3
return A.G(p.ca("/actions/"+a+"/execute",A.j(["params",c,"confirm",b],t.N,t.X)),$async$dk)
case 3:o=e
q=new A.j_(A.r(o.j(0,"ticketId")),A.r(o.j(0,"status")))
s=1
break
case 1:return A.O(q,r)}})
return A.P($async$dk,r)},
dr(){var s=0,r=A.Q(t.h6),q,p=this,o
var $async$dr=A.R(function(a,b){if(a===1)return A.N(b,r)
for(;;)switch(s){case 0:s=3
return A.G(p.af("/incidents"),$async$dr)
case 3:o=b
q=A.Ee(p.b7(A.bt(A.bs(o.e)).a7(o.w)))
s=1
break
case 1:return A.O(q,r)}})
return A.P($async$dr,r)},
ce(){var s=0,r=A.Q(t.aC),q,p=this,o
var $async$ce=A.R(function(a,b){if(a===1)return A.N(b,r)
for(;;)switch(s){case 0:s=3
return A.G(p.af("/environment"),$async$ce)
case 3:o=b
q=A.DP(p.b7(A.bt(A.bs(o.e)).a7(o.w)))
s=1
break
case 1:return A.O(q,r)}})
return A.P($async$ce,r)},
dg(){var s=0,r=A.Q(t.jd),q,p=this,o
var $async$dg=A.R(function(a,b){if(a===1)return A.N(b,r)
for(;;)switch(s){case 0:s=3
return A.G(p.af("/config"),$async$dg)
case 3:o=b
q=A.yx(p.b7(A.bt(A.bs(o.e)).a7(o.w)))
s=1
break
case 1:return A.O(q,r)}})
return A.P($async$dg,r)},
da(a){return this.lQ(t.G.a(a))},
lQ(a){var s=0,r=A.Q(t.jd),q,p=this,o
var $async$da=A.R(function(b,c){if(b===1)return A.N(c,r)
for(;;)switch(s){case 0:o=A
s=3
return A.G(p.aP("/config",a),$async$da)
case 3:q=o.yx(c)
s=1
break
case 1:return A.O(q,r)}})
return A.P($async$da,r)},
aR(a){var s=0,r=A.Q(t.jd),q,p=this,o
var $async$aR=A.R(function(b,c){if(b===1)return A.N(c,r)
for(;;)switch(s){case 0:o=A
s=3
return A.G(p.ca("/config/preset/"+a,B.a0),$async$aR)
case 3:q=o.yx(c)
s=1
break
case 1:return A.O(q,r)}})
return A.P($async$aR,r)},
dv(a){var s=0,r=A.Q(t.h),q,p=this,o,n,m
var $async$dv=A.R(function(b,c){if(b===1)return A.N(c,r)
for(;;)switch(s){case 0:s=3
return A.G(p.af("/logs?limit="+a),$async$dv)
case 3:n=c
m=p.bl(A.bt(A.bs(n.e)).a7(n.w)).j(0,"data")
if(!t._.b(m))throw A.d(B.iR)
o=J.aU(m,new A.qQ(),t.N)
o=A.x(o,o.$ti.h("z.E"))
q=o
s=1
break
case 1:return A.O(q,r)}})
return A.P($async$dv,r)},
$ipS:1,
$idi:1,
$iE8:1,
$iE7:1,
$iE5:1,
$iE6:1,
$iE9:1,
$iEb:1,
$iEa:1}
A.qM.prototype={
$1(a){return A.jE(t.P.a(a))},
$S:116}
A.qP.prototype={
$1(a){var s
t.P.a(a)
s=A.r(a.j(0,"id"))
A.r(a.j(0,"label"))
return new A.dO(s)},
$S:117}
A.qO.prototype={
$1(a){t.gc.a(a)
return A.mO(1,a.a,B.l,!0)+"="+A.mO(1,a.b,B.l,!0)},
$S:41}
A.qR.prototype={
$1(a){return A.Bg(t.P.a(a))},
$S:118}
A.qN.prototype={
$1(a){return A.Dq(t.P.a(a))},
$S:119}
A.qQ.prototype={
$1(a){return A.r(a)},
$S:11}
A.dm.prototype={
k(a){return"ReactAuthException: "+this.a},
$iaj:1}
A.b8.prototype={
k(a){return"ReactUnavailable: "+this.a},
$iaj:1}
A.f1.prototype={
k(a){return"ReactForbidden: "+this.a},
$iaj:1}
A.f2.prototype={
k(a){return"ReactNotFound: "+this.a},
$iaj:1}
A.f0.prototype={
k(a){return"ReactConflict: "+this.a},
$iaj:1}
A.mP.prototype={
j7(a){var s=this,r=a.f?"wss":"ws",q=A.mO(2,a.e,B.l,!1)
q=A.p(new v.G.WebSocket(r+"://"+a.c+":"+a.d+"/ws/logs?token="+q))
s.b!==$&&A.bT()
s.b=q
q.addEventListener("message",A.dA(new A.x_(s)))
q.addEventListener("error",A.dA(new A.x0(s)))
q.addEventListener("close",A.dA(new A.x1(s)))},
a_(){var s=0,r=A.Q(t.H),q,p=this,o
var $async$a_=A.R(function(a,b){if(a===1)return A.N(b,r)
for(;;)switch(s){case 0:if(p.c){s=1
break}p.c=!0
o=p.b
o===$&&A.S()
o.close()
o=p.a
s=(o.c&4)===0?3:4
break
case 3:s=5
return A.G(o.a_(),$async$a_)
case 5:case 4:case 1:return A.O(q,r)}})
return A.P($async$a_,r)},
$iyF:1}
A.x_.prototype={
$1(a){var s,r,q,p
A.p(a)
q=this.a.a
if((q.c&4)!==0)return
s=A.xU(a.data)
if(typeof s!="string")return
try{r=t.P.a(B.k.aJ(s,null))
if(J.a8(J.be(r,"type"),"log")&&typeof J.be(r,"line")=="string")q.m(0,A.r(J.be(r,"line")))}catch(p){return}},
$S:7}
A.x0.prototype={
$1(a){var s
A.p(a)
s=this.a.a
if((s.c&4)!==0)return
s.hy(new A.dw("WebSocket error on /ws/logs"))
if((s.c&4)===0)s.a_()},
$S:7}
A.x1.prototype={
$1(a){var s
A.p(a)
s=this.a.a
if((s.c&4)===0)s.a_()},
$S:7}
A.mQ.prototype={
j8(a){var s=this,r=a.f?"wss":"ws",q=A.mO(2,a.e,B.l,!1)
q=A.p(new v.G.WebSocket(r+"://"+a.c+":"+a.d+"/ws/metrics?token="+q))
s.b!==$&&A.bT()
s.b=q
q.addEventListener("message",A.dA(new A.x2(s)))
q.addEventListener("error",A.dA(new A.x3(s)))
q.addEventListener("close",A.dA(new A.x4(s)))},
a_(){var s=0,r=A.Q(t.H),q,p=this,o
var $async$a_=A.R(function(a,b){if(a===1)return A.N(b,r)
for(;;)switch(s){case 0:if(p.c){s=1
break}p.c=!0
o=p.b
o===$&&A.S()
o.close()
o=p.a
s=(o.c&4)===0?3:4
break
case 3:s=5
return A.G(o.a_(),$async$a_)
case 5:case 4:case 1:return A.O(q,r)}})
return A.P($async$a_,r)},
$iyG:1}
A.x2.prototype={
$1(a){var s,r,q,p
A.p(a)
q=this.a.a
if((q.c&4)!==0)return
s=A.xU(a.data)
if(typeof s!="string")return
try{r=t.P.a(B.k.aJ(s,null))
q.m(0,A.B1(r))}catch(p){return}},
$S:7}
A.x3.prototype={
$1(a){var s
A.p(a)
s=this.a.a
if((s.c&4)!==0)return
s.hy(new A.dw("WebSocket error on /ws/metrics"))
if((s.c&4)===0)s.a_()},
$S:7}
A.x4.prototype={
$1(a){var s
A.p(a)
s=this.a.a
if((s.c&4)===0)s.a_()},
$S:7}
A.fK.prototype={}
A.nC.prototype={
dV(){var s=this.b.$0()
return s},
T(){var s=0,r=A.Q(t.H),q=1,p=[],o=[],n=this,m,l,k
var $async$T=A.R(function(a,b){if(a===1){p.push(b)
s=q}for(;;)switch(s){case 0:n.e=!0
n.dV()
q=3
s=6
return A.G(n.a.d7(),$async$T)
case 6:n.d=b
o.push(5)
s=4
break
case 3:q=2
k=p.pop()
m=A.a1(k)
n.c.$1(m)
o.push(5)
s=4
break
case 2:o=[1]
case 4:q=1
n.e=!1
n.dV()
s=o.pop()
break
case 5:return A.O(null,r)
case 1:return A.N(p.at(-1),r)}})
return A.P($async$T,r)},
dj(a,b,c){return this.ml(a,t.G.a(b),c)},
ml(a,b,c){var s=0,r=A.Q(t.H),q=1,p=[],o=this,n,m,l,k,j,i,h
var $async$dj=A.R(function(d,e){if(d===1){p.push(e)
s=q}for(;;)switch(s){case 0:q=3
s=6
return A.G(o.a.dk(a,c,b),$async$dj)
case 6:n=e
l=o.r
k=n.a
j=n.b
Date.now()
B.b.cg(l,0,new A.fK(a,k,j))
l=o.r
if(l.length>20)o.r=B.b.b4(l,0,20)
o.dV()
q=1
s=5
break
case 3:q=2
h=p.pop()
m=A.a1(h)
o.c.$1(m)
s=5
break
case 2:s=1
break
case 5:return A.O(null,r)
case 1:return A.N(p.at(-1),r)}})
return A.P($async$dj,r)}}
A.nE.prototype={
$2(a,b){var s,r=t.e
r.a(a)
r.a(b)
s=B.c.P(A.iT(b.d),A.iT(a.d))
if(s!==0)return s
return B.a.P(a.b,b.b)},
$S:27}
A.nF.prototype={
si9(a){this.d=a
this.a.bU("reactor.alerts.thresholds",B.k.bb(a.a0(),null))},
hZ(a){var s,r,q,p,o,n,m,l,k,j,i,h
t.hg.a(a)
s=A.cJ(t.N)
for(r=a.length,q=0;q<a.length;a.length===r||(0,A.I)(a),++q){p=a[q]
s.m(0,p.a+"/"+p.c)}r=this.e
o=A.n(r).h("aW<1>")
n=o.h("a3<m.E>")
m=A.yO(new A.a3(new A.aW(r,o),o.h("y(m.E)").a(new A.nH(s)),n),n.h("m.E"))
for(o=A.Bq(m,m.r,A.n(m).c),n=o.$ti.c;o.p();){l=o.d
r.J(0,l==null?n.a(l):l)}k=A.a([],t.a2)
for(o=a.length,q=0;q<a.length;a.length===o||(0,A.I)(a),++q){p=a[q]
n=this.c
n===$&&A.S()
l=p.a
j=p.c
i=l+"/"+j
if(n.v(0,i))continue
h=r.dz(i,new A.nI(p))
B.b.m(k,new A.bm(l,p.b,j,p.d,p.e,p.f,p.r,p.w,h,p.y))}B.b.ai(k,new A.nJ())
return k},
md(a){var s,r,q,p,o
t.hg.a(a)
s=A.cJ(t.N)
for(r=a.length,q=0;q<a.length;a.length===r||(0,A.I)(a),++q){p=a[q]
if(p.d===B.E)s.m(0,p.a+"/"+p.c)}o=s.cd(this.f)
this.f=s
return o},
fV(a){var s,r,q,p,o=this.a.cp(a)
if(o==null)return A.cJ(t.N)
try{s=t._.a(B.k.aJ(o,null))
q=J.aU(s,new A.nG(),t.N).fd(0)
return q}catch(p){r=A.a1(p)
A.w(r)
return A.cJ(t.N)}},
km(){var s,r,q,p,o,n,m,l,k,j,i="msptWarn",h="incidentScoreWarn",g="gcPercentWarn",f="pingP95Warn",e="memoryPressureWarn",d=this.a.cp("reactor.alerts.thresholds")
if(d==null)return B.F
try{s=t.P.a(B.k.aJ(d,null))
q=s
p=q.K("tpsWarn")?A.at(q.j(0,"tpsWarn")):18
o=q.K("tpsCrit")?A.at(q.j(0,"tpsCrit")):10
n=q.K(i)?A.at(q.j(0,i)):50
m=q.K(h)?A.at(q.j(0,h)):50
l=q.K(g)?A.at(q.j(0,g)):15
k=q.K(f)?A.at(q.j(0,f)):200
q=q.K(e)?A.at(q.j(0,e)):90
return new A.fL(p,o,n,m,l,k,q)}catch(j){r=A.a1(j)
A.w(r)
return B.F}}}
A.nH.prototype={
$1(a){return!this.a.v(0,A.r(a))},
$S:5}
A.nI.prototype={
$0(){return this.a.x},
$S:120}
A.nJ.prototype={
$2(a,b){var s,r,q=t.e
q.a(a)
q.a(b)
s=B.c.P(A.iT(b.d),A.iT(a.d))
if(s!==0)return s
r=B.a.P(a.b,b.b)
if(r!==0)return r
return B.a.P(a.c,b.c)},
$S:27}
A.nG.prototype={
$1(a){return A.r(a)},
$S:11}
A.jz.prototype={
bk(){var s=this.b.$0()
return s},
T(){var s=0,r=A.Q(t.H),q=1,p=[],o=[],n=this,m,l,k
var $async$T=A.R(function(a,b){if(a===1){p.push(b)
s=q}for(;;)switch(s){case 0:n.f=!0
n.bk()
q=3
s=6
return A.G(n.a.dg(),$async$T)
case 6:n.d=b
n.e=A.t(t.N,t.X)
n.w=null
o.push(5)
s=4
break
case 3:q=2
k=p.pop()
m=A.a1(k)
n.w=m
n.c.$1(m)
o.push(5)
s=4
break
case 2:o=[1]
case 4:q=1
n.f=!1
n.bk()
s=o.pop()
break
case 5:return A.O(null,r)
case 1:return A.N(p.at(-1),r)}})
return A.P($async$T,r)},
mf(a,b){this.e.i(0,a,b)
this.bk()},
d9(){var s=0,r=A.Q(t.H),q,p=2,o=[],n=[],m=this,l,k,j,i,h,g
var $async$d9=A.R(function(a,b){if(a===1){o.push(b)
s=p}for(;;)switch(s){case 0:if(m.e.a===0){s=1
break}m.bk()
p=4
j=t.N
i=t.X
s=7
return A.G(m.a.da(A.ce(m.e,j,i)),$async$d9)
case 7:l=b
m.d=l
m.e=A.t(j,i)
m.w=null
n.push(6)
s=5
break
case 4:p=3
g=o.pop()
k=A.a1(g)
m.w=k
m.c.$1(k)
n.push(6)
s=5
break
case 3:n=[2]
case 5:p=2
m.bk()
s=n.pop()
break
case 6:case 1:return A.O(q,r)
case 2:return A.N(o.at(-1),r)}})
return A.P($async$d9,r)},
aR(a){return this.lR(a)},
lR(a){var s=0,r=A.Q(t.H),q=1,p=[],o=[],n=this,m,l,k,j
var $async$aR=A.R(function(b,c){if(b===1){p.push(c)
s=q}for(;;)switch(s){case 0:n.bk()
q=3
s=6
return A.G(n.a.aR(a),$async$aR)
case 6:m=c
n.d=m
n.e=A.t(t.N,t.X)
n.w=null
o.push(5)
s=4
break
case 3:q=2
j=p.pop()
l=A.a1(j)
n.w=l
n.c.$1(l)
o.push(5)
s=4
break
case 2:o=[1]
case 4:q=1
n.bk()
s=o.pop()
break
case 5:return A.O(null,r)
case 1:return A.N(p.at(-1),r)}})
return A.P($async$aR,r)}}
A.bU.prototype={
E(){return"ConnState."+this.b}}
A.h1.prototype={
c_(){var s,r=this
if(r.r)return
r.r=!0
r.f=B.ab
r.x=0
s=r.d
if(s!=null){r.Q=s
r.z=!1
s=s.a
r.as=new A.aM(s,A.n(s).h("aM<1>")).hT(r.gh8(),r.gh6(),r.gh7())}else r.eB()},
aq(){var s,r=this
r.r=!1
s=r.as
if(s!=null)s.W()
r.as=null
s=r.Q
if(s!=null)s.a_()
r.Q=null
s=r.at
if((s.c&4)===0)s.a_()
s=r.ax
if((s.c&4)===0)s.a_()},
kA(a){t.c.a(a)
if(!this.r)return
this.z=!0
this.h3(a)},
kz(a,b){var s,r=this
A.az(a)
t.l.a(b)
if(!r.r)return
s=r.as
if(s!=null)s.W()
r.as=null
if(r.z)r.eq()
r.eB()
r.hi()},
ky(){var s,r=this
if(!r.r)return
s=r.as
if(s!=null)s.W()
r.as=null
if(r.z)r.eq()
r.eB()
r.hi()},
hi(){var s,r,q=this
if(q.e==null)return
s=q.z?q.fA():B.O
r=t.H
A.yC(s,r).ah(new A.ot(q),r)},
eB(){if(!this.w){this.w=!0
this.bC()}},
bC(){var s=0,r=A.Q(t.H),q,p=2,o=[],n=[],m=this,l,k,j,i,h
var $async$bC=A.R(function(a,b){if(a===1){o.push(b)
s=p}for(;;)switch(s){case 0:p=3
k=t.H,j=m.a
case 6:if(!m.r){s=7
break}if(m.as!=null){n=[1]
s=4
break}p=9
s=12
return A.G(j.aN(),$async$bC)
case 12:l=b
if(!m.r){s=7
break}if(m.as!=null){s=7
break}m.h3(l)
p=3
s=11
break
case 9:p=8
h=o.pop()
s=A.a1(h) instanceof A.b8?13:15
break
case 13:if(!m.r){s=7
break}m.eq()
s=16
return A.G(A.yC(m.fA(),k),$async$bC)
case 16:s=6
break
s=14
break
case 15:throw h
case 14:s=11
break
case 8:s=3
break
case 11:s=17
return A.G(A.yC(B.O,k),$async$bC)
case 17:s=6
break
case 7:n.push(5)
s=4
break
case 3:n=[2]
case 4:p=2
m.w=!1
s=n.pop()
break
case 5:case 1:return A.O(q,r)
case 2:return A.N(o.at(-1),r)}})
return A.P($async$bC,r)},
h3(a){var s,r,q,p,o,n,m,l,k,j,i,h=this
h.x=0;++h.y
for(s=a.a,r=new A.aC(s,A.n(s).h("aC<1,2>")).gC(0),q=h.ay;r.p();){p=r.d
o=q.dz(p.a,new A.os())
n=p.b.d
m=o.d
l=o.a
k=o.b
j=o.c
if(m<l){B.b.i(k,B.c.bX(j+m,l),n);++o.d}else{B.b.i(k,j,n)
o.c=(o.c+1)%l}}r=h.y
i=h.f
h.f=B.N
if(i!==B.N&&(h.ax.c&4)===0)h.ax.m(0,B.N)
q=h.at
if((q.c&4)===0)q.m(0,new A.b9(s,a.b,r))},
eq(){var s=this,r=++s.x,q=s.f
r=r===1?s.f=B.B:s.f=B.w
if(q!==r&&(s.ax.c&4)===0)s.ax.m(0,r)},
fA(){var s=this.x
if(s<=0)return B.Z
return A.Ap(0,Math.min(B.c.a3(2000*B.c.iD(1,s-1),0,3e4),3e4))}}
A.ot.prototype={
$1(a){var s,r,q=this.a
if(!q.r)return
q.z=!1
s=q.e.$0()
q.Q=s
r=q.as
if(r!=null)r.W()
r=s.a
q.as=new A.aM(r,A.n(r).h("aM<1>")).hT(q.gh8(),q.gh6(),q.gh7())},
$S:49}
A.os.prototype={
$0(){return new A.f6(128,A.bL(128,0,!1,t.r))},
$S:121}
A.eB.prototype={
bA(){var s=this.c.$0()
return s},
T(){var s=0,r=A.Q(t.H),q=1,p=[],o=[],n=this,m,l,k,j
var $async$T=A.R(function(a,b){if(a===1){p.push(b)
s=q}for(;;)switch(s){case 0:n.f=!0
n.bA()
q=3
l=n.a
s=n.b?6:8
break
case 6:s=9
return A.G(l.c6("/tweaks"),$async$T)
case 9:s=7
break
case 8:s=10
return A.G(l.c6("/features"),$async$T)
case 10:case 7:n.e=b
o.push(5)
s=4
break
case 3:q=2
j=p.pop()
m=A.a1(j)
n.d.$1(m)
o.push(5)
s=4
break
case 2:o=[1]
case 4:q=1
n.f=!1
n.bA()
s=o.pop()
break
case 5:return A.O(null,r)
case 1:return A.N(p.at(-1),r)}})
return A.P($async$T,r)},
bT(a,b){return this.nh(a,b)},
nh(a3,a4){var s=0,r=A.Q(t.H),q,p=2,o=[],n=[],m=this,l,k,j,i,h,g,f,e,d,c,b,a,a0,a1,a2
var $async$bT=A.R(function(a5,a6){if(a5===1){o.push(a6)
s=p}for(;;)switch(s){case 0:a1=J.cq(m.e,new A.oE(a3))
if(a1<0){s=1
break}l=J.be(m.e,a1)
e=t.j
d=A.x(m.e,e)
B.b.i(d,a1,l.m5(a4))
m.e=A.al(d,e)
m.bA()
p=4
c=m.a
s=m.b?7:9
break
case 7:s=10
return A.G(c.cE(a3,a4),$async$bT)
case 10:s=8
break
case 9:s=11
return A.G(c.cB(a3,a4),$async$bT)
case 11:case 8:k=a6
b=A.x(m.e,e)
j=b
i=J.cq(j,new A.oF(a3))
c=i
if(typeof c!=="number"){q=c.bV()
n=[1]
s=5
break}if(c>=0){J.d9(j,i,k)
m.e=A.al(j,e)}n.push(6)
s=5
break
case 4:p=3
a2=o.pop()
h=A.a1(a2)
c=m.e
a0=A.x(c,e)
g=a0
f=J.cq(g,new A.oG(a3))
c=f
if(typeof c!=="number"){q=c.bV()
n=[1]
s=5
break}if(c>=0){J.d9(g,f,l)
m.e=A.al(g,e)}m.d.$1(h)
n.push(6)
s=5
break
case 3:n=[2]
case 5:p=2
m.bA()
s=n.pop()
break
case 6:case 1:return A.O(q,r)
case 2:return A.N(o.at(-1),r)}})
return A.P($async$bT,r)},
bx(a,b,c){return this.iB(a,b,c)},
iB(a5,a6,a7){var s=0,r=A.Q(t.H),q,p=2,o=[],n=[],m=this,l,k,j,i,h,g,f,e,d,c,b,a,a0,a1,a2,a3,a4
var $async$bx=A.R(function(a8,a9){if(a8===1){o.push(a9)
s=p}for(;;)switch(s){case 0:a3=J.cq(m.e,new A.oB(a5))
if(a3<0){s=1
break}l=J.be(m.e,a3)
e=t.j
d=A.x(m.e,e)
B.b.i(d,a3,l.nn(a6,a7))
m.e=A.al(d,e)
m.bA()
p=4
c=t.N
b=t.X
a=m.a
s=m.b?7:9
break
case 7:s=10
return A.G(a.cD(a5,A.j([a6,a7],c,b)),$async$bx)
case 10:s=8
break
case 9:s=11
return A.G(a.cA(a5,A.j([a6,a7],c,b)),$async$bx)
case 11:case 8:k=a9
a0=A.x(m.e,e)
j=a0
i=J.cq(j,new A.oC(a5))
c=i
if(typeof c!=="number"){q=c.bV()
n=[1]
s=5
break}if(c>=0){J.d9(j,i,k)
m.e=A.al(j,e)}n.push(6)
s=5
break
case 4:p=3
a4=o.pop()
h=A.a1(a4)
c=m.e
a2=A.x(c,e)
g=a2
f=J.cq(g,new A.oD(a5))
c=f
if(typeof c!=="number"){q=c.bV()
n=[1]
s=5
break}if(c>=0){J.d9(g,f,l)
m.e=A.al(g,e)}m.d.$1(h)
n.push(6)
s=5
break
case 3:n=[2]
case 5:p=2
m.bA()
s=n.pop()
break
case 6:case 1:return A.O(q,r)
case 2:return A.N(o.at(-1),r)}})
return A.P($async$bx,r)},
cz(a,b){var s=0,r=A.Q(t.H),q=this,p,o,n,m
var $async$cz=A.R(function(c,d){if(c===1)return A.N(d,r)
for(;;)switch(s){case 0:m=A.x(q.e,t.j)
p=m.length,o=0
case 2:if(!(o<m.length)){s=4
break}n=m[o]
s=n.d!==b?5:6
break
case 5:s=7
return A.G(q.bT(n.a,b),$async$cz)
case 7:case 6:case 3:m.length===p||(0,A.I)(m),++o
s=2
break
case 4:return A.O(null,r)}})
return A.P($async$cz,r)}}
A.oE.prototype={
$1(a){return t.j.a(a).a===this.a},
$S:6}
A.oF.prototype={
$1(a){return t.j.a(a).a===this.a},
$S:6}
A.oG.prototype={
$1(a){return t.j.a(a).a===this.a},
$S:6}
A.oB.prototype={
$1(a){return t.j.a(a).a===this.a},
$S:6}
A.oC.prototype={
$1(a){return t.j.a(a).a===this.a},
$S:6}
A.oD.prototype={
$1(a){return t.j.a(a).a===this.a},
$S:6}
A.h6.prototype={
aO(a){return this.d!=t.A.a(a).d}}
A.eF.prototype={
U(){return new A.mb()}}
A.mb.prototype={
jy(a){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e,d=a.H(t.T),c=A.cA(a)
if(d==null||c==null)return
s=d.e
if(s===this.d)return
this.d=s
s=d.d
r=A.F(s)
q=r.h("E<1,+id,name,snapshot(b,b,b9?)>")
p=A.x(new A.E(s,r.h("+id,name,snapshot(b,b,b9?)(1)").a(new A.uH()),q),q.h("z.E"))
s=c.f
s===$&&A.S()
r=s.d
r===$&&A.S()
o=A.yp(new A.b6(Date.now(),0,!1),p,r)
n=s.md(o)
m=A.t(t.N,t.e)
for(s=o.length,l=0;l<o.length;o.length===s||(0,A.I)(o),++l){k=o[l]
m.i(0,k.a+"/"+k.c,k)}for(s=n.gC(n),r=t.I,q=t.u;s.p();){j=m.j(0,s.gu())
if(j!=null){i=j.b
h=j.e
g=$.bD
if(g==null)g=$.bD=new A.cX(A.a([],r),A.a([],q),B.y)
f=Date.now()
e=g.c
B.b.cg(g.a,0,new A.cn("toast_"+f,"Critical alert",null,i+": "+h,B.an,6000,null,null,e))
g.h0()}}},
l(a){this.jy(a)
return this.a.d}}
A.uH.prototype={
$1(a){t.d.a(a)
return new A.ei(a.a,a.b,a.d)},
$S:28}
A.dh.prototype={}
A.xs.prototype={
$1(a){return t.C.a(a).a0()},
$S:38}
A.yb.prototype={
$1(a){var s,r,q
A.p(a)
s=A.a7(this.a.files)
if(s==null||A.bb(s.length)===0)return
r=A.a7(s.item(0))
if(r==null)return
q=A.p(new v.G.FileReader())
q.addEventListener("load",A.dA(new A.ya(q,this.b)))
q.readAsText(r)},
$S:7}
A.ya.prototype={
$1(a){var s,r
A.p(a)
s=this.a.result
if(s==null)return
r=A.xU(s)
if(typeof r!="string")return
this.b.$1(r)},
$S:7}
A.bX.prototype={}
A.iy.prototype={
siG(a){this.f=t.nz.a(a)},
siH(a){this.r=t.ky.a(a)}}
A.oX.prototype={
gir(){var s=this.b,r=A.F(s),q=r.h("bi<1,aV>")
s=A.x(new A.bi(new A.a3(s,r.h("y(1)").a(new A.p_(this)),r.h("a3<1>")),r.h("aV(1)").a(new A.p0(this)),q),q.h("m.E"))
return s},
aH(a){var s,r,q,p,o,n,m,l,k,j,i,h=this
t.lT.a(a)
s=A.F(a)
r=new A.E(a,s.h("b(1)").a(new A.p1()),s.h("E<1,b>")).fd(0)
s=h.b
q=A.F(s)
p=q.h("a3<1>")
o=A.x(new A.a3(s,q.h("y(1)").a(new A.p2(r)),p),p.h("m.E"))
q=o.length
n=q!==0
if(n){for(p=h.c,m=0;m<o.length;o.length===q||(0,A.I)(o),++m){l=o[m]
k=p.j(0,l)
if(k!=null){j=k.f
if(j!=null)j.W()
j=k.r
if(j!=null)j.W()
p.J(0,l)}}B.b.i1(s,new A.p3(r))}for(q=a.length,p=h.c,m=0;m<a.length;a.length===q||(0,A.I)(a),++m){i=a[m]
j=i.a
if(!p.K(j)){k=new A.iy(i.c,j,i.b)
p.i(0,j,k)
B.b.m(s,j)
h.fz(i,k)
n=!0}}if(!h.d&&n)h.a.$0()},
aq(){var s,r,q,p
this.d=!0
for(s=this.c,r=new A.bh(s,s.r,s.e,A.n(s).h("bh<2>"));r.p();){q=r.d
p=q.f
if(p!=null)p.W()
q=q.r
if(q!=null)q.W()}s.O(0)
B.b.O(this.b)},
lp(a){var s,r,q,p,o,n,m
t.lT.a(a)
for(s=a.length,r=this.c,q=this.b,p=0;p<a.length;a.length===s||(0,A.I)(a),++p){o=a[p]
n=o.a
m=new A.iy(o.c,n,o.b)
r.i(0,n,m)
B.b.m(q,n)
this.fz(o,m)}},
fz(a,b){b.siG(a.d.bP(new A.oY(this,b)))
b.siH(a.e.bP(new A.oZ(this,b)))}}
A.p_.prototype={
$1(a){return this.a.c.K(A.r(a))},
$S:5}
A.p0.prototype={
$1(a){var s=this.a.c.j(0,A.r(a))
return new A.aV(s.d,s.e,s.a,s.b,s.c)},
$S:126}
A.p1.prototype={
$1(a){return t.ii.a(a).a},
$S:127}
A.p2.prototype={
$1(a){return!this.a.v(0,A.r(a))},
$S:5}
A.p3.prototype={
$1(a){return!this.a.v(0,A.r(a))},
$S:5}
A.oY.prototype={
$1(a){var s,r
t.c.a(a)
s=this.a
if(s.d)return
r=this.b
r.b=a
r.c=new A.b6(Date.now(),0,!1)
s.a.$0()},
$S:19}
A.oZ.prototype={
$1(a){var s
t.x.a(a)
s=this.a
if(s.d)return
this.b.a=a
s.a.$0()},
$S:16}
A.hc.prototype={
aO(a){return this.e!==t.T.a(a).e}}
A.dN.prototype={
U(){return new A.md()}}
A.md.prototype={
aW(){var s,r,q=this
q.bi()
s=q.a.d
r=new A.oX(new A.uO(q),A.a([],t.s),A.t(t.N,t.cH))
r.lp(s)
q.d=r},
bq(a){var s,r
t.hX.a(a)
this.c0(a)
s=this.a.d
if(a.d!==s){r=this.d
r===$&&A.S()
r.aH(s)}},
aq(){var s=this.d
s===$&&A.S()
s.aq()
this.by()},
l(a){var s=this.d
s===$&&A.S()
return new A.hc(s.gir(),this.e,this.a.e,null)}}
A.uO.prototype={
$0(){var s=this.a
return s.t(new A.uN(s))},
$S:0}
A.uN.prototype={
$0(){return this.a.e++},
$S:0}
A.p4.prototype={
m(a,b){var s=0,r=A.Q(t.H),q=this,p,o
var $async$m=A.R(function(c,d){if(c===1)return A.N(d,r)
for(;;)switch(s){case 0:o=q.ex(b)
s=2
return A.G(o.aK(),$async$m)
case 2:p=b.a
q.f.i(0,p,o)
B.b.m(q.d,b)
q.e.i(0,p,q.e0(b,o))
q.cV()
return A.O(null,r)}})
return A.P($async$m,r)},
n8(a,b){var s,r=this.d,q=B.b.bM(r,new A.p9(a))
if(q<0)return
if(!(q<r.length))return A.f(r,q)
s=r[q]
B.b.i(r,q,new A.bq(s.a,b,s.c,s.d,s.e,s.f,s.r,s.w,s.x))
this.cV()},
J(a,b){var s,r=this
B.b.i1(r.d,new A.p8(b))
s=r.e.J(0,b)
if(s!=null)s.aq()
r.f.J(0,b)
if(r.r===b)r.r=null
r.cV()},
mB(a){var s,r,q,p,o,n,m,l,k=this
t.jO.a(a)
k.eg()
s=k.d
B.b.O(s)
r=k.e
r.O(0)
q=k.f
q.O(0)
for(p=a.length,o=0;n=a.length,o<n;a.length===p||(0,A.I)(a),++o){m=a[o]
l=k.ex(m)
n=m.a
q.i(0,n,l)
B.b.m(s,m)
r.i(0,n,k.e0(m,l))}k.r=n===0?null:B.b.gaz(a).a
k.cV()},
i7(a){var s=this.f.j(0,a)
return s instanceof A.ci?s:null},
mM(a){var s,r,q,p,o
A.r(a)
r=this.d
q=r.length
p=0
for(;;){if(!(p<q)){s=null
break}o=r[p]
if(o.a===a){s=o
break}++p}if(s==null)return null
return new A.p7(s)},
eg(){for(var s=this.e,s=new A.bh(s,s.r,s.e,A.n(s).h("bh<2>"));s.p();)s.d.aq()},
lz(){var s,r,q,p,o,n,m,l,k,j,i,h=this,g=h.a.cp("reactor.fleet")
if(g==null)return
try{s=t._.a(B.k.aJ(g,null))
for(n=J.aE(s),m=t.P,l=h.f,k=h.d,j=h.e;n.p();){r=n.gu()
q=A.B0(m.a(r))
p=h.ex(q)
l.i(0,q.a,p)
B.b.m(k,q)
j.i(0,q.a,h.e0(q,p))}}catch(i){o=A.a1(i)
A.w(o)}},
ex(a){var s,r,q=this,p=a.r
if(p==null||p.length===0)return q.b.$1(a)
s=a.c.length!==0&&a.d>0?q.b.$1(a):null
p=q.c
r=p==null?null:p.$1(a)
p=s==null
if(p&&r==null)return q.b.$1(a)
if(p&&r==null)A.ak(A.ai("At least one of direct or relay must be non-null",null))
return new A.jX(s,r,a.x,B.al)},
e0(a,b){var s=a.r
if(s!=null&&s.length!==0)return A.Ak(b,null,null)
return A.Ak(b,A.BR(a),new A.p5(a))},
cV(){var s=this.d,r=A.F(s),q=r.h("E<1,L<b,@>>")
s=A.x(new A.E(s,r.h("L<b,@>(1)").a(new A.p6()),q),q.h("z.E"))
this.a.bU("reactor.fleet",B.k.bb(s,null))}}
A.p9.prototype={
$1(a){return t.C.a(a).a===this.a},
$S:47}
A.p8.prototype={
$1(a){return t.C.a(a).a===this.a},
$S:47}
A.p7.prototype={
$0(){return A.G_(this.a)},
$S:129}
A.p5.prototype={
$0(){return A.BR(this.a)},
$S:130}
A.p6.prototype={
$1(a){return t.C.a(a).a0()},
$S:38}
A.aV.prototype={}
A.dg.prototype={
E(){return"FleetHealth."+this.b}}
A.bx.prototype={}
A.jS.prototype={}
A.pa.prototype={
$1(a){return t.e.a(a).a===this.a.a},
$S:37}
A.pd.prototype={
$1(a){switch(a.a){case 3:return 0
case 2:return 1
case 1:return 2
case 0:return 3}},
$S:131}
A.pb.prototype={
$1(a){var s
t.eR.a(a)
if(a.r===B.aJ){s=a.c
s=s===B.w||s===B.B||a.w>0}else s=!0
return s},
$S:132}
A.pc.prototype={
$2(a,b){var s,r=t.eR
r.a(a)
r.a(b)
r=this.a
s=J.yn(r.$1(a.r),r.$1(b.r))
if(s!==0)return s
return B.c.P(b.w,a.w)},
$S:133}
A.hd.prototype={
aO(a){return this.e!==t.ne.a(a).e}}
A.hi.prototype={
aO(a){return this.d!=t.fI.a(a).d}}
A.qh.prototype={
gnm(){var s,r,q,p=this.r
if(p==="ALL")return A.al(this.e,t.N)
s=this.e
r=A.F(s)
q=r.h("a3<1>")
p=A.x(new A.a3(s,r.h("y(1)").a(new A.qj(p.toUpperCase())),q),q.h("m.E"))
return p},
bB(){var s=this.c.$0()
return s},
T(){var s=200
return this.mK()},
mK(){var s=0,r=A.Q(t.H),q=1,p=[],o=[],n=this,m,l,k,j,i,h
var $async$T=A.R(function(a,b){if(a===1){p.push(b)
s=q}for(;;)switch(s){case 0:i=200
n.bB()
q=3
s=6
return A.G(n.a.dv(i),$async$T)
case 6:m=b
k=n.e
B.b.O(k)
B.b.B(k,m)
n.hp()
o.push(5)
s=4
break
case 3:q=2
h=p.pop()
l=A.a1(h)
o.push(5)
s=4
break
case 2:o=[1]
case 4:q=1
n.bB()
s=o.pop()
break
case 5:return A.O(null,r)
case 1:return A.N(p.at(-1),r)}})
return A.P($async$T,r)},
c_(){var s,r=this,q=r.b
if(r.x||q==null)return
r.x=!0
s=q.a
r.y=new A.aM(s,A.n(s).h("aM<1>")).bP(new A.qi(r))},
hp(){for(var s=this.e;s.length>1000;)B.b.bR(s,0)}}
A.qj.prototype={
$1(a){return B.a.v(A.r(a).toUpperCase(),this.a)},
$S:5}
A.qi.prototype={
$1(a){var s
A.r(a)
s=this.a
if(s.f)return
B.b.m(s.e,a)
s.hp()
s.bB()},
$S:2}
A.hD.prototype={
aO(a){t.Y.a(a)
return this.d!=a.d||!J.a8(this.e,a.e)}}
A.hL.prototype={
aO(a){var s,r
t.U.a(a)
s=this.d
s=s==null?null:s.a
r=a.d
return s!=(r==null?null:r.a)}}
A.dX.prototype={
U(){return new A.mz()}}
A.mz.prototype={
a5(){var s,r=this
r.ar()
s=r.a.d
if(s!=null&&!r.e){r.e=!0
r.cR(s)}},
cR(a){return this.jW(a)},
jW(a){var s=0,r=A.Q(t.H),q,p=2,o=[],n=this,m,l,k
var $async$cR=A.R(function(b,c){if(b===1){o.push(c)
s=p}for(;;)switch(s){case 0:p=4
s=7
return A.G(a.cv(),$async$cR)
case 7:m=c
if(n.c==null){s=1
break}n.t(new A.vT(n,m))
p=2
s=6
break
case 4:p=3
k=o.pop()
s=1
break
s=6
break
case 3:s=2
break
case 6:case 1:return A.O(q,r)
case 2:return A.N(o.at(-1),r)}})
return A.P($async$cR,r)},
l(a){return new A.hL(this.d,this.a.e,null)}}
A.vT.prototype={
$0(){return this.a.d=this.b},
$S:0}
A.mU.prototype={}
A.hO.prototype={
aO(a){var s,r
t.V.a(a)
if(this.e===a.e){s=this.d
s=s==null?null:s.c
r=a.d
s=s!=(r==null?null:r.c)}else s=!0
return s}}
A.rD.prototype={
c7(){var s,r,q,p=this,o=p.b
if(o!=null)return o
s=p.a.cp("reactor.fleet.tags")
if(s==null)return p.b=A.t(t.N,t.h)
try{r=t.P.a(B.k.aJ(s,null))
p.b=J.Dm(r,new A.rE(),t.N,t.h)}catch(q){p.b=A.t(t.N,t.h)}o=p.b
o.toString
return o},
fa(a){var s=this.c7().j(0,a)
if(s==null)s=A.a([],t.s)
return A.al(s,t.N)},
fk(a,b){var s,r,q,p
t.h.a(b)
s=this.c7()
r=A.F(b)
q=r.h("a3<1>")
q=A.yO(new A.a3(b,r.h("y(1)").a(new A.rH()),q),q.h("m.E"))
p=A.x(q,A.n(q).c)
B.b.fl(p)
if(p.length===0)s.J(0,a)
else s.i(0,a,p)
this.a.bU("reactor.fleet.tags",B.k.bb(this.c7(),null))},
lP(){var s,r,q=A.cJ(t.N)
for(s=this.c7(),s=new A.bh(s,s.r,s.e,A.n(s).h("bh<2>"));s.p();)q.B(0,s.d)
r=A.x(q,q.$ti.c)
B.b.fl(r)
return r},
iq(a){var s=this.c7(),r=A.n(s).h("aC<1,2>"),q=r.h("bi<m.E,b>")
s=A.x(new A.bi(new A.a3(new A.aC(s,r),r.h("y(m.E)").a(new A.rF(a)),r.h("a3<m.E>")),r.h("b(m.E)").a(new A.rG()),q),q.h("m.E"))
return s}}
A.rE.prototype={
$2(a,b){return new A.W(A.r(a),J.Dl(t._.a(b),t.N),t.cW)},
$S:134}
A.rH.prototype={
$1(a){return A.r(a).length!==0},
$S:5}
A.rF.prototype={
$1(a){return J.zB(t.cW.a(a).b,this.a)},
$S:135}
A.rG.prototype={
$1(a){return t.cW.a(a).a},
$S:136}
A.lG.prototype={
cp(a){return A.aA(A.p(A.p(v.G.window).localStorage).getItem(a))},
bU(a,b){return A.p(A.p(v.G.window).localStorage).setItem(a,b)},
J(a,b){return A.p(A.p(v.G.window).localStorage).removeItem(b)},
$iDW:1}
A.lI.prototype={
d2(){var s=this.b.$0()
return s},
T(){var s=0,r=A.Q(t.H),q=1,p=[],o=[],n=this,m,l,k
var $async$T=A.R(function(a,b){if(a===1){p.push(b)
s=q}for(;;)switch(s){case 0:n.e=!0
n.d2()
q=3
s=6
return A.G(n.a.dL(),$async$T)
case 6:n.d=b
o.push(5)
s=4
break
case 3:q=2
k=p.pop()
m=A.a1(k)
n.c.$1(m)
o.push(5)
s=4
break
case 2:o=[1]
case 4:q=1
n.e=!1
n.d2()
s=o.pop()
break
case 5:return A.O(null,r)
case 1:return A.N(p.at(-1),r)}})
return A.P($async$T,r)},
b3(a,b,c,d){return this.iy(a,b,c,d)},
iu(a){return this.b3(a,null,null,null)},
ix(a,b){return this.b3(a,null,null,b)},
iw(a,b){return this.b3(a,null,b,null)},
iv(a,b){return this.b3(a,b,null,null)},
iy(a7,a8,a9,b0){var s=0,r=A.Q(t.H),q,p=2,o=[],n=[],m=this,l,k,j,i,h,g,f,e,d,c,b,a,a0,a1,a2,a3,a4,a5,a6
var $async$b3=A.R(function(b1,b2){if(b1===1){o.push(b2)
s=p}for(;;)switch(s){case 0:a5=J.cq(m.d,new A.tp(a7))
if(a5<0){s=1
break}l=J.be(m.d,a5)
e=t.q
d=A.x(m.d,e)
c=l
b=c.a
a=c.b
a0=a8==null?c.c:a8
a1=a9==null?c.d:a9
B.b.i(d,a5,new A.c2(b,a,a0,a1,b0==null?c.e:b0))
m.d=A.al(d,e)
m.d2()
p=4
s=7
return A.G(m.a.cF(a7,a8,a9,b0),$async$b3)
case 7:k=b2
a2=A.x(m.d,e)
j=a2
i=J.cq(j,new A.tq(a7))
c=i
if(typeof c!=="number"){q=c.bV()
n=[1]
s=5
break}if(c>=0){J.d9(j,i,k)
m.d=A.al(j,e)}n.push(6)
s=5
break
case 4:p=3
a6=o.pop()
h=A.a1(a6)
c=m.d
a4=A.x(c,e)
g=a4
f=J.cq(g,new A.tr(a7))
c=f
if(typeof c!=="number"){q=c.bV()
n=[1]
s=5
break}if(c>=0){J.d9(g,f,l)
m.d=A.al(g,e)}m.c.$1(h)
n.push(6)
s=5
break
case 3:n=[2]
case 5:p=2
m.d2()
s=n.pop()
break
case 6:case 1:return A.O(q,r)
case 2:return A.N(o.at(-1),r)}})
return A.P($async$b3,r)}}
A.tp.prototype={
$1(a){return t.q.a(a).a===this.a},
$S:21}
A.tq.prototype={
$1(a){return t.q.a(a).a===this.a},
$S:21}
A.tr.prototype={
$1(a){return t.q.a(a).a===this.a},
$S:21}
A.dV.prototype={
E(){return"ReactorStatus."+this.b}}
A.kI.prototype={
l(a){var s,r=this,q=null,p=t.N
p=A.B(A.j(["display","flex","flex-direction","column","gap","24px","width","100%"],p,p))
s=A.a([new A.kJ(r.d,r.e,r.f,r.r,q)],t.i)
B.b.B(s,r.w)
return new A.c(q,"reactor-page",p,q,q,s,q)}}
A.kJ.prototype={
l(a){var s,r=this,q=null,p=r.r,o=r.f,n=t.i,m=A.a([],n)
if(p!=null)m.push(p)
s=A.a([new A.c(q,q,B.lI,q,q,A.a([new A.k(r.d,q)],n),q)],n)
s.push(new A.c(q,q,B.kF,q,q,A.a([new A.k(r.e,q)],n),q))
m.push(new A.c(q,q,B.bC,q,q,s,q))
m=A.a([new A.c(q,q,B.kL,q,q,m,q)],n)
if(o!=null)m.push(new A.c(q,q,B.aq,q,q,A.a([o],n),q))
return new A.c(q,"reactor-page-header",B.l9,q,q,m,q)}}
A.e0.prototype={
l(a){var s,r,q,p,o,n,m,l,k=this,j=null,i=k.w
if(i==null){s=A.a([],t.i)
r=k.r
if(r!=null)s.push(r)
i=s}q=i.length!==0
s=t.i
r=A.a([],s)
p=k.e
o=k.f
n=t.N
m=A.t(n,n)
m.i(0,"display","flex")
m.i(0,"align-items","center")
m.i(0,"justify-content","space-between")
m.i(0,"gap","1rem")
m.i(0,"padding","0.85rem 1.15rem")
if(q)m.i(0,"border-bottom",u.h)
m.i(0,"background","linear-gradient(90deg, color-mix(in srgb, var(--primary) 8%, transparent), transparent 72%)")
m=A.B(m)
l=A.a([],s)
l.push(A.H(A.a([new A.k(k.d,j)],s),j,j,j,B.z))
if(p!=null)l.push(new A.c(j,j,B.lD,j,j,A.a([new A.k(p,j)],s),j))
l=A.a([new A.c(j,j,B.kx,j,j,l,j)],s)
if(o!=null)l.push(new A.c(j,j,B.kK,j,j,A.a([o],s),j))
r.push(new A.c(j,j,m,j,j,l,j))
if(q)r.push(new A.c(j,j,A.B(A.j(["padding",k.y?"0":"1.15rem","display","flex","flex-direction","column","gap","16px"],n,n)),j,j,i,j))
return A.jp("0.5rem",new A.c(j,"reactor-panel",B.ar,j,j,r,j),!0,"0")}}
A.kj.prototype={
l(a){var s=this,r=null,q=t.i,p=A.a([A.H(A.a([new A.k(s.d,r)],q),r,r,r,B.z)],q),o=A.a([A.H(A.a([new A.k(s.e,r)],q),r,r,r,B.kP)],q)
p.push(new A.c(r,r,B.bG,r,r,o,r))
p=A.a([new A.c(r,r,B.ko,r,r,p,r)],q)
p.push(s.w)
return A.jp("0.5rem",new A.c(r,"reactor-metric-card",B.ar,r,r,A.a([new A.c(r,r,B.kA,r,r,p,r),new A.c(r,r,B.kO,r,r,A.a([s.x],q),r)],q),r),!0,"0")}}
A.kL.prototype={
l(a){var s=null,r=this.f,q=t.i,p=A.H(A.a([new A.k(this.d,s)],q),s,s,s,B.z),o=A.a([A.H(A.a([new A.k(this.e,s)],q),s,s,s,B.l_)],q)
if(r!=null)o.push(A.H(A.a([new A.k(r,s)],q),s,s,s,B.bB))
q=A.a([p,new A.c(s,s,B.bG,s,s,o,s)],q)
return new A.c(s,s,B.kC,s,s,q,s)}}
A.kK.prototype={
l(a){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e,d=this,c=null,b=d.d
if(b.length<2){s=t.N
return new A.c(c,c,A.B(A.j(["height",""+d.f+"px"],s,s)),c,c,B.n,c)}r=B.b.gaz(b)
q=B.b.gaz(b)
for(s=b.length,p=0;p<s;++p){o=b[p]
if(o<r)r=o
if(o>q)q=o}n=q-r
if(Math.abs(n)<1e-9)n=1
m=100/(s-1)
l=new A.aI("")
for(k=0,s="";k<b.length;++k){j=b[k]
l.a=s+(k===0?"M ":"L ")
s=B.e.Z(k*m,2)+" "+B.e.Z(94-(j-r)/n*88,2)+" "
s=l.a+=s}s=l.k(0)
j=B.c.Z(100,2)
i="spark-"+A.eo(d)
h=t.N
g=A.j(["width","100%","height",""+d.f,"viewBox","0 0 100 100","preserveAspectRatio","none","aria-hidden","true"],h,h)
f=d.e
e=t.i
e=A.a([new A.X("defs",c,c,c,c,c,A.a([new A.X("linearGradient",c,c,c,A.j(["id",i,"x1","0","y1","0","x2","0","y2","1"],h,h),c,A.a([new A.X("stop",c,c,c,A.j(["offset","0%","stop-color",f,"stop-opacity","0.28"],h,h),c,c,c),new A.X("stop",c,c,c,A.j(["offset","100%","stop-color",f,"stop-opacity","0"],h,h),c,c,c)],e),c)],e),c)],e)
e.push(new A.X("path",c,c,c,A.j(["d",s+" L "+j+" 100 L 0 100 Z","fill","url(#"+i+")","stroke","none"],h,h),c,c,c))
s=l.a
e.push(new A.X("path",c,c,c,A.j(["d",B.a.aG(s.charCodeAt(0)==0?s:s),"fill","none","stroke",f,"stroke-width","2","stroke-linecap","round","stroke-linejoin","round","vector-effect","non-scaling-stroke"],h,h),c,c,c))
return new A.X("svg",c,c,c,g,c,e,c)}}
A.jC.prototype={
l(a){var s,r,q=null,p=this.d,o=t.i,n=A.a([new A.fe(p.b,B.cQ,B.aN,B.bv,B.aS,"h3",q)],o),m=p.e
if(m.length!==0)n.push(A.zq(A.a([new A.k(m,q)],o),q,B.as))
o=A.a([new A.c(q,q,B.by,q,q,n,q)],o)
p=p.f
n=p.length
if(n===0)o.push(A.ct("This item has no tunable knobs.","No configurable options"))
else for(m=this.f,s=0;s<p.length;p.length===n||(0,A.I)(p),++s){r=p[s]
o.push(new A.dQ(r,m?q:new A.oo(this,r),m,q))}return A.bJ(o,16)}}
A.oo.prototype={
$1(a){var s=this.a.e
return s==null?null:s.$2(this.b.a,a)},
$S:14}
A.jB.prototype={
l(a){var s=this,r=null,q=s.e,p=q==null,o=p?r:q.b
if(p)q=new A.bK(B.n,r)
else{p=s.w
q=new A.jC(q,p?r:s.f,p,r)}return new A.ja(s.d,s.r,q,o,r)}}
A.hf.prototype={
E(){return"GaugeStatus."+this.b}}
A.jW.prototype={
l(a){var s,r,q,p,o,n,m,l,k,j,i,h,g=this,f=null,e=g.e,d=g.w,c=g.x?A.yD(g.r-e,d):A.yD(e,d),b=A.DZ(c)
d=g.r
s=d>0?B.e.a3(e/d,0,1):0
d=g.d
r=t.N
q=A.j(["viewBox","0 0 100 100","width","132","height","132","aria-label",d],r,r)
p=Math.cos(-2.356194490192345)
o=Math.sin(-2.356194490192345)
n=Math.cos(2.356194490192345)
m=Math.sin(2.356194490192345)
l=t.i
m=A.a([new A.X("path",f,f,f,A.j(["d","M "+B.e.Z(50+38*p,2)+" "+B.e.Z(50+38*o,2)+" A 38 38 0 1 1 "+B.e.Z(50+38*n,2)+" "+B.e.Z(50+38*m,2),"fill","none","stroke","color-mix(in srgb, var(--border) 85%, transparent)","stroke-width","7","stroke-linecap","round"],r,r),f,f,f)],l)
if(s>0){k=4.71238898038469*B.e.a3(s,0,1)
j=-2.356194490192345+k
p=Math.cos(-2.356194490192345)
o=Math.sin(-2.356194490192345)
n=Math.cos(j)
i=Math.sin(j)
h=k>3.141592653589793?1:0
m.push(new A.X("path",f,f,f,A.j(["d","M "+B.e.Z(50+38*p,2)+" "+B.e.Z(50+38*o,2)+" A 38 38 0 "+h+" 1 "+B.e.Z(50+38*n,2)+" "+B.e.Z(50+38*i,2),"fill","none","stroke",b,"stroke-width","7","stroke-linecap","round","style","filter: drop-shadow(0 0 5px "+("color-mix(in srgb, "+b+" 55%, transparent)")+")"],r,r),f,f,f))}p=g.f
e=A.a([new A.c(f,f,B.m3,f,f,A.a([new A.k(p==null?B.e.Z(e,1):p,f)],l),f)],l)
if(c!==B.aO)e.push(new A.c(f,f,A.B(A.j(["font-size","0.625rem","font-weight","600","letter-spacing","0","text-transform","uppercase","color",b],r,r)),f,f,A.a([new A.k(A.E_(c),f)],l),f))
return new A.c(f,f,B.kB,f,f,A.a([new A.c(f,f,B.kw,f,f,A.a([new A.X("svg",f,f,f,q,f,m,f),new A.c(f,f,B.lP,f,f,e,f)],l),f),new A.c(f,f,B.kJ,f,f,A.a([new A.k(d,f)],l),f)],l),f)}}
A.jY.prototype={
l(a1){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e,d,c=null,b=this.d,a=b.c,a0=b.b
a=a.length!==0?a0+" \u2014 "+a:a0
a0=t.i
s=new A.c(c,c,B.as,c,c,A.a([new A.k(a,c)],a0),c)
if(b.x.length===0)return new A.c(c,c,c,c,c,A.a([s,new A.c(c,c,B.i,c,c,A.a([new A.k("No activity",c)],a0),c)],a0),c)
a=b.f
r=a*2+1
q=b.d-a
p=b.e-a
o=A.a([],a0)
for(a=t.N,n=b.r,m=b.w,l=m-n,k=m<=n+0.0001,j=0;j<r;++j)for(i=p+j,h=0;h<r;++h){g=q+h
f=b.lY(g,i)
if(f==null)B.b.m(o,new A.c(c,c,B.lU,c,c,B.n,c))
else{if(k){e=f.c
d=e>0?0.5:0}else{e=f.c
d=B.e.a3((e-n)/l,0,1)}B.b.m(o,new A.c(c,c,A.B(A.j(["background","rgb("+B.c.a3(B.e.ac(40+d*215),0,255)+",90,"+B.c.a3(B.e.ac(180+d*-140),0,255)+")"],a,a)),A.j(["data-cx",B.c.k(g),"data-cz",B.c.k(i),"data-score",B.e.k(e)],a,a),c,B.n,c))}}return new A.c(c,c,c,c,c,A.a([s,new A.c(c,c,A.B(A.j(["display","grid","grid-template-columns","repeat("+r+", 1fr)","gap","1px","aspect-ratio","1/1","width","100%","max-width","320px"],a,a)),c,c,o,c),new A.c(c,c,B.lC,c,c,A.a([new A.k(B.e.Z(n,2),c),new A.k(B.e.Z(m,2),c)],a0),c)],a0),c)}}
A.dQ.prototype={
l(a){var s,r,q,p,o,n,m,l=this,k=null,j=l.d
switch(j.c.a){case 0:s=J.a8(j.d,!0)
r=l.f
q=r?k:new A.q3(l)
s=A.nR(r,j.b,q,s)
break
case 1:s=j.d
if(typeof s=="number")s=B.e.bv(s)
else{s=A.hH(A.w(s),k)
if(s==null)s=0}s=B.c.k(s)
r=l.f
q=r?k:new A.q4(l)
s=A.cW(r,k,!1,k,j.b,q,k,k,B.W,s)
break
case 2:s=j.d
if(!(typeof s=="number")){s=A.bZ(A.w(s))
if(s==null)s=0}s=B.e.k(s)
r=l.f
q=r?k:new A.q5(l)
s=A.cW(r,k,!1,k,j.b,q,k,k,B.W,s)
break
case 3:s=j.gfn()
r=l.f
q=r?k:new A.q6(l)
s=A.cW(r,k,!1,k,j.b,q,k,k,B.V,s)
break
case 4:s=j.gfn()
r=l.f
q=A.a([],t.lZ)
for(p=j.e,o=p.length,n=0;n<p.length;p.length===o||(0,A.I)(p),++n){m=p[n]
q.push(new A.ah(m,m,!1))}p=r?k:new A.q7(l)
s=A.yq(r,k,!1,k,j.b,k,p,q,k,!1,B.A,s)
break
default:s=k}r=t.i
s=A.a([s],r)
j=j.f
if(j.length!==0)s.push(new A.c(k,k,B.at,k,k,A.a([new A.k(j,k)],r),k))
return new A.c(k,k,B.by,k,k,s,k)}}
A.q3.prototype={
$1(a){var s=this.a.e
return s==null?null:s.$1(a)},
$S:9}
A.q4.prototype={
$1(a){var s,r=A.hH(a.aG(0),null)
if(r!=null){s=this.a.e
if(s!=null)s.$1(r)}},
$S:2}
A.q5.prototype={
$1(a){var s,r=A.bZ(a.aG(0))
if(r!=null){s=this.a.e
if(s!=null)s.$1(r)}},
$S:2}
A.q6.prototype={
$1(a){var s=this.a.e
return s==null?null:s.$1(a)},
$S:2}
A.q7.prototype={
$1(a){var s=this.a.e
return s==null?null:s.$1(a)},
$S:2}
A.cO.prototype={
l(a){var s,r,q=this.d
if(q==null)return new A.bK(B.n,null)
s=q.a
A:{if("admin"===s){r=B.bY
break A}if("operator"===s){r=B.bX
break A}r=B.bZ
break A}return r}}
A.dr.prototype={
l(a){var s,r,q,p=null,o=this.e,n=A.EX(o),m=o==null,l=m?p:o.c
if(l==null)l=""
s=m?p:o.w
if(s==null)s=A.a([],t.gk)
m=t.i
r=A.a([A.H(A.a([new A.k(this.d,p)],m),p,p,p,B.z)],m)
q=A.a([A.H(A.a([new A.k(n,p)],m),p,p,p,B.km)],m)
if(l.length!==0)q.push(A.H(A.a([new A.k(l,p)],m),p,p,p,B.bB))
m=A.a([new A.c(p,p,B.ma,p,p,r,p),new A.c(p,p,B.lF,p,p,q,p)],m)
if(s.length>=2)m.push(new A.kK(s,"color-mix(in srgb, var(--muted-foreground) 70%, transparent)",34,p))
return A.jp("0.5rem",new A.c(p,"reactor-metric-card",B.kp,p,p,m,p),!0,"0")}}
A.fc.prototype={
l(a){var s=this.d,r=A.dC(A.EY(s),A.B7(s),8)
return r}}
A.rU.prototype={
gn(a){return this.c.length},
gmI(){return this.b.length},
j3(a,b){var s,r,q,p,o,n,m,l,k,j
for(s=this.c,r=s.length,q=a.a,p=q.length,o=s.$flags|0,n=this.b,m=0;m<r;++m){if(!(m<p))return A.f(q,m)
l=q.charCodeAt(m)
o&2&&A.au(s)
s[m]=l
if(l===13){k=m+1
if(k<p){if(!(k<p))return A.f(q,k)
j=q.charCodeAt(k)!==10}else j=!0
if(j)l=10}if(l===10)B.b.m(n,m+1)}},
bW(a){var s,r=this
if(a<0)throw A.d(A.b1("Offset may not be negative, was "+a+"."))
else if(a>r.c.length)throw A.d(A.b1("Offset "+a+u.s+r.gn(0)+"."))
s=r.b
if(a<B.b.gaz(s))return-1
if(a>=B.b.gaL(s))return s.length-1
if(r.ki(a)){s=r.d
s.toString
return s}return r.d=r.jg(a)-1},
ki(a){var s,r,q,p=this.d
if(p==null)return!1
s=this.b
r=s.length
if(p>>>0!==p||p>=r)return A.f(s,p)
if(a<s[p])return!1
if(!(p>=r-1)){q=p+1
if(!(q<r))return A.f(s,q)
q=a<s[q]}else q=!0
if(q)return!0
if(!(p>=r-2)){q=p+2
if(!(q<r))return A.f(s,q)
q=a<s[q]
s=q}else s=!0
if(s){this.d=p+1
return!0}return!1},
jg(a){var s,r,q=this.b,p=q.length,o=p-1
for(s=0;s<o;){r=s+B.c.ag(o-s,2)
if(!(r>=0&&r<p))return A.f(q,r)
if(q[r]>a)o=r
else s=r+1}return o},
dN(a){var s,r,q,p=this
if(a<0)throw A.d(A.b1("Offset may not be negative, was "+a+"."))
else if(a>p.c.length)throw A.d(A.b1("Offset "+a+" must be not be greater than the number of characters in the file, "+p.gn(0)+"."))
s=p.bW(a)
r=p.b
if(!(s>=0&&s<r.length))return A.f(r,s)
q=r[s]
if(q>a)throw A.d(A.b1("Line "+s+" comes after offset "+a+"."))
return a-q},
cw(a){var s,r,q,p
if(a<0)throw A.d(A.b1("Line may not be negative, was "+a+"."))
else{s=this.b
r=s.length
if(a>=r)throw A.d(A.b1("Line "+a+" must be less than the number of lines in the file, "+this.gmI()+"."))}q=s[a]
if(q<=this.c.length){p=a+1
s=p<r&&q>=s[p]}else s=!0
if(s)throw A.d(A.b1("Line "+a+" doesn't have 0 columns."))
return q}}
A.jR.prototype={
gR(){return this.a.a},
gY(){return this.a.bW(this.b)},
ga4(){return this.a.dN(this.b)},
ga6(){return this.b}}
A.fn.prototype={
gR(){return this.a.a},
gn(a){return this.c-this.b},
gG(){return A.yB(this.a,this.b)},
gF(){return A.yB(this.a,this.c)},
gad(){return A.hW(B.af.b4(this.a.c,this.b,this.c),0,null)},
gao(){var s=this,r=s.a,q=s.c,p=r.bW(q)
if(r.dN(q)===0&&p!==0){if(q-s.b===0)return p===r.b.length-1?"":A.hW(B.af.b4(r.c,r.cw(p),r.cw(p+1)),0,null)}else q=p===r.b.length-1?r.c.length:r.cw(p+1)
return A.hW(B.af.b4(r.c,r.cw(r.bW(s.b)),q),0,null)},
P(a,b){var s
t.hs.a(b)
if(!(b instanceof A.fn))return this.j0(0,b)
s=B.c.P(this.b,b.b)
return s===0?B.c.P(this.c,b.c):s},
N(a,b){var s=this
if(b==null)return!1
if(!(b instanceof A.fn))return s.j_(0,b)
return s.b===b.b&&s.c===b.c&&J.a8(s.a.a,b.a.a)},
gI(a){return A.cL(this.b,this.c,this.a.a,B.d,B.d,B.d,B.d,B.d,B.d,B.d)},
$icT:1}
A.pv.prototype={
my(){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e,d,c,b,a=this,a0=null,a1=a.a
a.hw(B.b.gaz(a1).c)
s=a.e
r=A.bL(s,a0,!1,t.dd)
for(q=a.r,s=s!==0,p=a.b,o=0;o<a1.length;++o){n=a1[o]
if(o>0){m=a1[o-1]
l=n.c
if(!J.a8(m.c,l)){a.d4("\u2575")
q.a+="\n"
a.hw(l)}else if(m.b+1!==n.b){a.lL("...")
q.a+="\n"}}for(l=n.d,k=A.F(l).h("cN<1>"),j=new A.cN(l,k),j=new A.aw(j,j.gn(0),k.h("aw<z.E>")),k=k.h("z.E"),i=n.b,h=n.a;j.p();){g=j.d
if(g==null)g=k.a(g)
f=g.a
if(f.gG().gY()!==f.gF().gY()&&f.gG().gY()===i&&a.kj(B.a.q(h,0,f.gG().ga4()))){e=B.b.aU(r,a0)
if(e<0)A.ak(A.ai(A.w(r)+" contains no null elements.",a0))
B.b.i(r,e,g)}}a.lK(i)
q.a+=" "
a.lJ(n,r)
if(s)q.a+=" "
d=B.b.bM(l,new A.pQ())
if(d===-1)c=a0
else{if(!(d>=0&&d<l.length))return A.f(l,d)
c=l[d]}k=c!=null
if(k){j=c.a
g=j.gG().gY()===i?j.gG().ga4():0
a.lH(h,g,j.gF().gY()===i?j.gF().ga4():h.length,p)}else a.d6(h)
q.a+="\n"
if(k)a.lI(n,c,r)
for(l=l.length,b=0;b<l;++b)continue}a.d4("\u2575")
a1=q.a
return a1.charCodeAt(0)==0?a1:a1},
hw(a){var s,r,q=this
if(!q.f||!t.R.b(a))q.d4("\u2577")
else{q.d4("\u250c")
q.au(new A.pD(q),"\x1b[34m",t.H)
s=q.r
r=" "+$.zz().hY(a)
s.a+=r}q.r.a+="\n"},
d3(a,b,c){var s,r,q,p,o,n,m,l,k,j,i,h,g,f=this,e={}
t.eU.a(b)
e.a=!1
e.b=null
s=c==null
if(s)r=null
else r=f.b
for(q=b.length,p=t.a,o=f.b,s=!s,n=f.r,m=t.H,l=!1,k=0;k<q;++k){j=b[k]
i=j==null
h=i?null:j.a.gG().gY()
g=i?null:j.a.gF().gY()
if(s&&j===c){f.au(new A.pK(f,h,a),r,p)
l=!0}else if(l)f.au(new A.pL(f,j),r,p)
else if(i)if(e.a)f.au(new A.pM(f),e.b,m)
else n.a+=" "
else f.au(new A.pN(e,f,c,h,a,j,g),o,p)}},
lJ(a,b){return this.d3(a,b,null)},
lH(a,b,c,d){var s=this
s.d6(B.a.q(a,0,b))
s.au(new A.pE(s,a,b,c),d,t.H)
s.d6(B.a.q(a,c,a.length))},
lI(a,b,c){var s,r,q,p=this
t.eU.a(c)
s=p.b
r=b.a
if(r.gG().gY()===r.gF().gY()){p.eF()
r=p.r
r.a+=" "
p.d3(a,c,b)
if(c.length!==0)r.a+=" "
p.hx(b,c,p.au(new A.pF(p,a,b),s,t.S))}else{q=a.b
if(r.gG().gY()===q){if(B.b.v(c,b))return
A.Ie(c,b,t.D)
p.eF()
r=p.r
r.a+=" "
p.d3(a,c,b)
p.au(new A.pG(p,a,b),s,t.H)
r.a+="\n"}else if(r.gF().gY()===q){r=r.gF().ga4()
if(r===a.a.length){A.CL(c,b,t.D)
return}p.eF()
p.r.a+=" "
p.d3(a,c,b)
p.hx(b,c,p.au(new A.pH(p,!1,a,b),s,t.S))
A.CL(c,b,t.D)}}},
hv(a,b,c){var s=c?0:1,r=this.r
s=B.a.aB("\u2500",1+b+this.ee(B.a.q(a.a,0,b+s))*3)
r.a=(r.a+=s)+"^"},
lG(a,b){return this.hv(a,b,!0)},
hx(a,b,c){t.eU.a(b)
this.r.a+="\n"
return},
d6(a){var s,r,q,p
for(s=new A.c9(a),r=t.gS,s=new A.aw(s,s.gn(0),r.h("aw<T.E>")),q=this.r,r=r.h("T.E");s.p();){p=s.d
if(p==null)p=r.a(p)
if(p===9)q.a+=B.a.aB(" ",4)
else{p=A.am(p)
q.a+=p}}},
d5(a,b,c){var s={}
s.a=c
if(b!=null)s.a=B.c.k(b+1)
this.au(new A.pO(s,this,a),"\x1b[34m",t.a)},
d4(a){return this.d5(a,null,null)},
lL(a){return this.d5(null,null,a)},
lK(a){return this.d5(null,a,null)},
eF(){return this.d5(null,null,null)},
ee(a){var s,r,q,p
for(s=new A.c9(a),r=t.gS,s=new A.aw(s,s.gn(0),r.h("aw<T.E>")),r=r.h("T.E"),q=0;s.p();){p=s.d
if((p==null?r.a(p):p)===9)++q}return q},
kj(a){var s,r,q
for(s=new A.c9(a),r=t.gS,s=new A.aw(s,s.gn(0),r.h("aw<T.E>")),r=r.h("T.E");s.p();){q=s.d
if(q==null)q=r.a(q)
if(q!==32&&q!==9)return!1}return!0},
au(a,b,c){var s,r
c.h("0()").a(a)
s=this.b!=null
if(s&&b!=null)this.r.a+=b
r=a.$0()
if(s&&b!=null)this.r.a+="\x1b[0m"
return r}}
A.pP.prototype={
$0(){return this.a},
$S:139}
A.px.prototype={
$1(a){var s=t.nR.a(a).d,r=A.F(s)
return new A.a3(s,r.h("y(1)").a(new A.pw()),r.h("a3<1>")).gn(0)},
$S:140}
A.pw.prototype={
$1(a){var s=t.D.a(a).a
return s.gG().gY()!==s.gF().gY()},
$S:20}
A.py.prototype={
$1(a){return t.nR.a(a).c},
$S:142}
A.pA.prototype={
$1(a){var s=t.D.a(a).a.gR()
return s==null?new A.u():s},
$S:143}
A.pB.prototype={
$2(a,b){var s=t.D
return s.a(a).a.P(0,s.a(b).a)},
$S:144}
A.pC.prototype={
$1(a0){var s,r,q,p,o,n,m,l,k,j,i,h,g,f,e,d,c,b,a
t.lO.a(a0)
s=a0.a
r=a0.b
q=A.a([],t.dg)
for(p=J.bl(r),o=p.gC(r),n=t.pg;o.p();){m=o.gu().a
l=m.gao()
k=A.xZ(l,m.gad(),m.gG().ga4())
k.toString
j=B.a.bF("\n",B.a.q(l,0,k)).gn(0)
i=m.gG().gY()-j
for(m=l.split("\n"),k=m.length,h=0;h<k;++h){g=m[h]
if(q.length===0||i>B.b.gaL(q).b)B.b.m(q,new A.bF(g,i,s,A.a([],n)));++i}}f=A.a([],n)
for(o=q.length,n=t.aP,e=f.$flags|0,d=0,h=0;h<q.length;q.length===o||(0,A.I)(q),++h){g=q[h]
m=n.a(new A.pz(g))
e&1&&A.au(f,16)
B.b.hh(f,m,!0)
c=f.length
for(m=p.aC(r,d),k=m.$ti,m=new A.aw(m,m.gn(0),k.h("aw<z.E>")),b=g.b,k=k.h("z.E");m.p();){a=m.d
if(a==null)a=k.a(a)
if(a.a.gG().gY()>b)break
B.b.m(f,a)}d+=f.length-c
B.b.B(g.d,f)}return q},
$S:145}
A.pz.prototype={
$1(a){return t.D.a(a).a.gF().gY()<this.a.b},
$S:20}
A.pQ.prototype={
$1(a){t.D.a(a)
return!0},
$S:20}
A.pD.prototype={
$0(){this.a.r.a+=B.a.aB("\u2500",2)+">"
return null},
$S:0}
A.pK.prototype={
$0(){var s=this.a.r,r=this.b===this.c.b?"\u250c":"\u2514"
s.a+=r},
$S:8}
A.pL.prototype={
$0(){var s=this.a.r,r=this.b==null?"\u2500":"\u253c"
s.a+=r},
$S:8}
A.pM.prototype={
$0(){this.a.r.a+="\u2500"
return null},
$S:0}
A.pN.prototype={
$0(){var s,r,q=this,p=q.a,o=p.a?"\u253c":"\u2502"
if(q.c!=null)q.b.r.a+=o
else{s=q.e
r=s.b
if(q.d===r){s=q.b
s.au(new A.pI(p,s),p.b,t.a)
p.a=!0
if(p.b==null)p.b=s.b}else{s=q.r===r&&q.f.a.gF().ga4()===s.a.length
r=q.b
if(s)r.r.a+="\u2514"
else r.au(new A.pJ(r,o),p.b,t.a)}}},
$S:8}
A.pI.prototype={
$0(){var s=this.b.r,r=this.a.a?"\u252c":"\u250c"
s.a+=r},
$S:8}
A.pJ.prototype={
$0(){this.a.r.a+=this.b},
$S:8}
A.pE.prototype={
$0(){var s=this
return s.a.d6(B.a.q(s.b,s.c,s.d))},
$S:0}
A.pF.prototype={
$0(){var s,r,q=this.a,p=q.r,o=p.a,n=this.c.a,m=n.gG().ga4(),l=n.gF().ga4()
n=this.b.a
s=q.ee(B.a.q(n,0,m))
r=q.ee(B.a.q(n,m,l))
m+=s*3
n=(p.a+=B.a.aB(" ",m))+B.a.aB("^",Math.max(l+(s+r)*3-m,1))
p.a=n
return n.length-o.length},
$S:18}
A.pG.prototype={
$0(){return this.a.lG(this.b,this.c.a.gG().ga4())},
$S:0}
A.pH.prototype={
$0(){var s=this,r=s.a,q=r.r,p=q.a
if(s.b)q.a=p+B.a.aB("\u2500",3)
else r.hv(s.c,Math.max(s.d.a.gF().ga4()-1,0),!1)
return q.a.length-p.length},
$S:18}
A.pO.prototype={
$0(){var s=this.b,r=s.r,q=this.a.a
if(q==null)q=""
s=B.a.mW(q,s.d)
s=r.a+=s
q=this.c
r.a=s+(q==null?"\u2502":q)},
$S:8}
A.aS.prototype={
k(a){var s=this.a
s="primary "+(""+s.gG().gY()+":"+s.gG().ga4()+"-"+s.gF().gY()+":"+s.gF().ga4())
return s.charCodeAt(0)==0?s:s}}
A.vh.prototype={
$0(){var s,r,q,p,o=this.a
if(!(t.ol.b(o)&&A.xZ(o.gao(),o.gad(),o.gG().ga4())!=null)){s=A.lc(o.gG().ga6(),0,0,o.gR())
r=o.gF().ga6()
q=o.gR()
p=A.Hg(o.gad(),10)
o=A.rV(s,A.lc(r,A.Bp(o.gad()),p,q),o.gad(),o.gad())}return A.Fk(A.Fm(A.Fl(o)))},
$S:146}
A.bF.prototype={
k(a){return""+this.b+': "'+this.a+'" ('+B.b.aA(this.d,", ")+")"}}
A.c1.prototype={
eN(a){var s=this.a
if(!J.a8(s,a.gR()))throw A.d(A.ai('Source URLs "'+A.w(s)+'" and "'+A.w(a.gR())+"\" don't match.",null))
return Math.abs(this.b-a.ga6())},
P(a,b){var s
t.hq.a(b)
s=this.a
if(!J.a8(s,b.gR()))throw A.d(A.ai('Source URLs "'+A.w(s)+'" and "'+A.w(b.gR())+"\" don't match.",null))
return this.b-b.ga6()},
N(a,b){if(b==null)return!1
return t.hq.b(b)&&J.a8(this.a,b.gR())&&this.b===b.ga6()},
gI(a){var s=this.a
s=s==null?null:s.gI(s)
if(s==null)s=0
return s+this.b},
k(a){var s=this,r=A.bH(s).k(0),q=s.a
return"<"+r+": "+s.b+" "+(A.w(q==null?"unknown source":q)+":"+(s.c+1)+":"+(s.d+1))+">"},
$iax:1,
gR(){return this.a},
ga6(){return this.b},
gY(){return this.c},
ga4(){return this.d}}
A.ld.prototype={
eN(a){if(!J.a8(this.a.a,a.gR()))throw A.d(A.ai('Source URLs "'+A.w(this.gR())+'" and "'+A.w(a.gR())+"\" don't match.",null))
return Math.abs(this.b-a.ga6())},
P(a,b){t.hq.a(b)
if(!J.a8(this.a.a,b.gR()))throw A.d(A.ai('Source URLs "'+A.w(this.gR())+'" and "'+A.w(b.gR())+"\" don't match.",null))
return this.b-b.ga6()},
N(a,b){if(b==null)return!1
return t.hq.b(b)&&J.a8(this.a.a,b.gR())&&this.b===b.ga6()},
gI(a){var s=this.a.a
s=s==null?null:s.gI(s)
if(s==null)s=0
return s+this.b},
k(a){var s=A.bH(this).k(0),r=this.b,q=this.a,p=q.a
return"<"+s+": "+r+" "+(A.w(p==null?"unknown source":p)+":"+(q.bW(r)+1)+":"+(q.dN(r)+1))+">"},
$iax:1,
$ic1:1}
A.le.prototype={
j4(a,b,c){var s,r=this.b,q=this.a
if(!J.a8(r.gR(),q.gR()))throw A.d(A.ai('Source URLs "'+A.w(q.gR())+'" and  "'+A.w(r.gR())+"\" don't match.",null))
else if(r.ga6()<q.ga6())throw A.d(A.ai("End "+r.k(0)+" must come after start "+q.k(0)+".",null))
else{s=this.c
if(s.length!==q.eN(r))throw A.d(A.ai('Text "'+s+'" must be '+q.eN(r)+" characters long.",null))}},
gG(){return this.a},
gF(){return this.b},
gad(){return this.c}}
A.lf.prototype={
geZ(){return this.a},
k(a){var s,r,q,p=this.b,o="line "+(p.gG().gY()+1)+", column "+(p.gG().ga4()+1)
if(p.gR()!=null){s=p.gR()
r=$.zz()
s.toString
s=o+(" of "+r.hY(s))
o=s}o+=": "+this.a
q=p.mz(null)
p=q.length!==0?o+"\n"+q:o
return"Error on "+(p.charCodeAt(0)==0?p:p)},
$iaj:1}
A.fa.prototype={
ga6(){var s=this.b
s=A.yB(s.a,s.b)
return s.b},
$ibn:1,
gcH(){return this.c}}
A.fb.prototype={
gR(){return this.gG().gR()},
gn(a){return this.gF().ga6()-this.gG().ga6()},
P(a,b){var s
t.hs.a(b)
s=this.gG().P(0,b.gG())
return s===0?this.gF().P(0,b.gF()):s},
mz(a){var s=this
if(!t.ol.b(s)&&s.gn(s)===0)return""
return A.E1(s,a).my()},
N(a,b){if(b==null)return!1
return b instanceof A.fb&&this.gG().N(0,b.gG())&&this.gF().N(0,b.gF())},
gI(a){return A.cL(this.gG(),this.gF(),B.d,B.d,B.d,B.d,B.d,B.d,B.d,B.d)},
k(a){var s=this
return"<"+A.bH(s).k(0)+": from "+s.gG().k(0)+" to "+s.gF().k(0)+' "'+s.gad()+'">'},
$iax:1,
$icj:1}
A.cT.prototype={
gao(){return this.d}}
A.lm.prototype={
gcH(){return A.r(this.c)}}
A.t1.prototype={
geY(){var s=this
if(s.c!==s.e)s.d=null
return s.d},
dP(a){var s,r=this,q=r.d=J.Dn(a,r.b,r.c)
r.e=r.c
s=q!=null
if(s)r.e=r.c=q.gF()
return s},
hG(a,b){var s
if(this.dP(a))return
if(b==null)if(a instanceof A.dP)b="/"+a.a+"/"
else{s=J.aF(a)
s=A.d8(s,"\\","\\\\")
b='"'+A.d8(s,'"','\\"')+'"'}this.fN(b)},
cf(a){return this.hG(a,null)},
mn(){if(this.c===this.b.length)return
this.fN("no more input")},
mk(a,b,c){var s,r,q,p,o,n=this.b
if(c<0)A.ak(A.b1("position must be greater than or equal to 0."))
else if(c>n.length)A.ak(A.b1("position must be less than or equal to the string length."))
s=c+b>n.length
if(s)A.ak(A.b1("position plus length must not go beyond the end of the string."))
s=this.a
r=A.a([0],t.lC)
q=n.length
p=new A.rU(s,r,new Uint32Array(q))
p.j3(new A.c9(n),s)
o=c+b
if(o>q)A.ak(A.b1("End "+o+u.s+p.gn(0)+"."))
else if(c<0)A.ak(A.b1("Start may not be negative, was "+c+"."))
throw A.d(new A.lm(n,a,new A.fn(p,c,o)))},
fN(a){this.mk("expected "+a+".",0,this.c)}}
A.yA.prototype={}
A.id.prototype={
aY(a,b,c,d){var s=A.n(this)
s.h("~(1)?").a(a)
t.Z.a(c)
return A.yZ(this.a,this.b,a,!1,s.c)}}
A.m6.prototype={}
A.ie.prototype={
W(){var s,r=this,q=A.pq(null,t.H),p=r.b
if(p==null)return q
s=r.d
if(s!=null)p.removeEventListener(r.c,s,!1)
r.d=r.b=null
return q},
$ibj:1}
A.uC.prototype={
$1(a){return this.a.$1(A.p(a))},
$S:4};(function aliases(){var s=J.dl.prototype
s.iU=s.k
s=A.bz.prototype
s.iO=s.hO
s.iP=s.hP
s.iR=s.hR
s.iQ=s.hQ
s=A.T.prototype
s.iV=s.bg
s=A.fS.prototype
s.iJ=s.bc
s=A.kT.prototype
s.iZ=s.eL
s=A.fT.prototype
s.fp=s.ap
s.dS=s.bQ
s=A.jx.prototype
s.iK=s.eH
s=A.C.prototype
s.cJ=s.ck
s.dT=s.ap
s.dU=s.aH
s.cI=s.bK
s.ft=s.dI
s.iM=s.bJ
s.iN=s.ff
s.iL=s.d1
s.fq=s.a5
s.fs=s.dh
s=A.hu.prototype
s.iS=s.ap
s=A.hx.prototype
s.iW=s.ap
s=A.eX.prototype
s.iX=s.aH
s=A.eS.prototype
s.iT=s.aH
s=A.bp.prototype
s.iY=s.bp
s=A.M.prototype
s.bi=s.aW
s.c0=s.bq
s.by=s.aq
s.ar=s.a5
s=A.fb.prototype
s.j0=s.P
s.j_=s.N})();(function installTearOffs(){var s=hunkHelpers._static_2,r=hunkHelpers._static_1,q=hunkHelpers._static_0,p=hunkHelpers.installInstanceTearOff,o=hunkHelpers._instance_2u,n=hunkHelpers._instance_0u,m=hunkHelpers._instance_1i,l=hunkHelpers.installStaticTearOff,k=hunkHelpers._instance_1u
s(J,"Gr","Ei",48)
r(A,"GZ","Fb",30)
r(A,"H_","Fc",30)
r(A,"H0","Fd",30)
r(A,"H1","GG",45)
q(A,"Cp","GR",0)
s(A,"H2","GI",26)
q(A,"Co","GH",0)
p(A.fk.prototype,"gm3",0,1,null,["$2","$1"],["df","bI"],149,0,0)
o(A.a_.prototype,"gjD","jE",26)
n(A.fl.prototype,"gku","kv",0)
s(A,"H8","Ga",56)
r(A,"H9","Gb",52)
s(A,"H7","Eq",48)
r(A,"Hb","Gc",44)
var j
m(j=A.lW.prototype,"glO","m",14)
n(j,"gm_","a_",0)
r(A,"Hf","HU",52)
s(A,"He","HT",56)
r(A,"Hc","F7",23)
q(A,"Hd","FS",151)
s(A,"Ct","GV",152)
n(A.iG.prototype,"gh4","kx",0)
n(A.i2.prototype,"glw","lx",0)
n(j=A.i1.prototype,"glu","lv",0)
n(j,"gka","kb",0)
n(j,"gkc","kd",0)
l(A,"Ht",0,null,["$1$size","$0"],["zH",function(){return A.zH(B.f)}],1,0)
l(A,"Hu",0,null,["$1$size","$0"],["zI",function(){return A.zI(B.f)}],1,0)
l(A,"Hv",0,null,["$1$size","$0"],["zJ",function(){return A.zJ(B.f)}],1,0)
l(A,"Hw",0,null,["$1$size","$0"],["zK",function(){return A.zK(B.f)}],1,0)
l(A,"Hx",0,null,["$1$size","$0"],["zL",function(){return A.zL(B.f)}],1,0)
l(A,"Hy",0,null,["$1$size","$0"],["zM",function(){return A.zM(B.f)}],1,0)
l(A,"Hz",0,null,["$1$size","$0"],["zN",function(){return A.zN(B.f)}],1,0)
l(A,"HA",0,null,["$1$size","$0"],["zO",function(){return A.zO(B.f)}],1,0)
l(A,"HB",0,null,["$1$size","$0"],["zP",function(){return A.zP(B.f)}],1,0)
l(A,"HC",0,null,["$1$size","$0"],["zQ",function(){return A.zQ(B.f)}],1,0)
l(A,"HD",0,null,["$1$size","$0"],["zR",function(){return A.zR(B.f)}],1,0)
l(A,"HE",0,null,["$1$size","$0"],["zS",function(){return A.zS(B.f)}],1,0)
l(A,"HF",0,null,["$1$size","$0"],["zT",function(){return A.zT(B.f)}],1,0)
l(A,"HG",0,null,["$1$size","$0"],["zU",function(){return A.zU(B.f)}],1,0)
l(A,"HH",0,null,["$1$size","$0"],["zV",function(){return A.zV(B.f)}],1,0)
l(A,"HI",0,null,["$1$size","$0"],["zW",function(){return A.zW(B.f)}],1,0)
l(A,"HJ",0,null,["$1$size","$0"],["zX",function(){return A.zX(B.f)}],1,0)
l(A,"HK",0,null,["$1$size","$0"],["zY",function(){return A.zY(B.f)}],1,0)
l(A,"HL",0,null,["$1$size","$0"],["zZ",function(){return A.zZ(B.f)}],1,0)
l(A,"HM",0,null,["$1$size","$0"],["A_",function(){return A.A_(B.f)}],1,0)
l(A,"HN",0,null,["$1$size","$0"],["A0",function(){return A.A0(B.f)}],1,0)
l(A,"HO",0,null,["$1$size","$0"],["A1",function(){return A.A1(B.f)}],1,0)
l(A,"HP",0,null,["$1$size","$0"],["A2",function(){return A.A2(B.f)}],1,0)
l(A,"HQ",0,null,["$1$size","$0"],["A3",function(){return A.A3(B.f)}],1,0)
l(A,"HR",0,null,["$1$size","$0"],["A4",function(){return A.A4(B.f)}],1,0)
l(A,"HS",0,null,["$1$size","$0"],["A5",function(){return A.A5(B.f)}],1,0)
r(A,"H6","Dw",23)
n(A.fZ.prototype,"gm4","eL",0)
s(A,"zh","DO",154)
r(A,"y_","Fn",15)
n(A.jm.prototype,"gn0","n1",0)
n(A.mj.prototype,"glB","lC",0)
l(A,"Id",4,null,["$6$extra$redirectHistory","$4","$5$extra"],["yf",function(a,b,c,d){return A.yf(a,b,c,d,null,null)},function(a,b,c,d,e){return A.yf(a,b,c,d,e,null)}],155,0)
k(A.dZ.prototype,"gkF","kG",53)
n(A.hI.prototype,"gkR","kS",0)
n(A.iF.prototype,"ghq","lA",0)
k(j=A.i0.prototype,"gks","kt",2)
n(j,"gjz","jA",0)
n(j,"gkZ","l_",0)
p(j,"gkw",0,0,null,["$1","$0"],["c9","h2"],93,0,0)
k(j=A.i8.prototype,"gjd","cL",105)
n(j,"gjc","cK",31)
n(A.ic.prototype,"gkT","kU",0)
n(A.iA.prototype,"gjs","jt",0)
o(A.jz.prototype,"gme","mf",36)
k(j=A.h1.prototype,"gh8","kA",19)
o(j,"gh7","kz",26)
n(j,"gh6","ky",0)
o(j=A.eB.prototype,"gfe","bT",122)
p(j,"giA",0,3,null,["$3"],["bx"],123,0,0)
m(j,"gis","cz",124)
p(A.lI.prototype,"git",0,1,null,["$4$budgetMs$panicMs$releaseMs","$1","$2$releaseMs","$2$panicMs","$2$budgetMs"],["b3","iu","ix","iw","iv"],137,0,0)
l(A,"I6",2,null,["$1$2","$2"],["CH",function(a,b){return A.CH(a,b,t.cZ)}],156,0)
l(A,"Cy",0,null,["$1$3$onChange$onClick$onInput","$0","$1$0","$1$1$onClick","$1$2$onChange$onInput"],["n3",function(){return A.n3(null,null,null,t.z)},function(a){return A.n3(null,null,null,a)},function(a,b){return A.n3(null,a,null,b)},function(a,b,c){return A.n3(a,null,b,c)}],104,0)})();(function inheritance(){var s=hunkHelpers.mixin,r=hunkHelpers.mixinHard,q=hunkHelpers.inherit,p=hunkHelpers.inheritMany
q(A.u,null)
p(A.u,[A.yL,J.k6,A.hM,J.dI,A.m,A.fY,A.bg,A.ad,A.T,A.rC,A.aw,A.hw,A.e9,A.hb,A.hR,A.h8,A.i_,A.av,A.co,A.br,A.eU,A.h2,A.ee,A.cR,A.te,A.ku,A.h9,A.iB,A.a5,A.qd,A.hv,A.bh,A.cH,A.dP,A.fp,A.ds,A.hV,A.mI,A.uc,A.c_,A.mf,A.mN,A.mL,A.lT,A.d5,A.aG,A.aH,A.fj,A.i5,A.e5,A.ig,A.fk,A.bE,A.a_,A.lU,A.fr,A.i4,A.d2,A.m0,A.c6,A.fl,A.mG,A.iP,A.ec,A.d3,A.mq,A.ef,A.iL,A.cx,A.jF,A.u8,A.o6,A.vp,A.wY,A.wV,A.b6,A.ca,A.us,A.kw,A.hS,A.dw,A.bn,A.W,A.aa,A.mJ,A.aI,A.iM,A.tk,A.bR,A.jQ,A.kt,A.e,A.M,A.cn,A.cX,A.ah,A.dF,A.cc,A.fN,A.dE,A.nt,A.fV,A.od,A.oe,A.or,A.oJ,A.rR,A.oU,A.ph,A.cg,A.qz,A.nN,A.ry,A.rT,A.rY,A.t4,A.e6,A.t9,A.tb,A.jc,A.lx,A.jV,A.t7,A.qK,A.U,A.c8,A.jk,A.fS,A.o0,A.eW,A.lR,A.bW,A.cK,A.cD,A.jO,A.C,A.jh,A.ud,A.mT,A.lQ,A.fv,A.mK,A.lo,A.kT,A.cm,A.jm,A.jx,A.de,A.mj,A.eR,A.bp,A.dU,A.rg,A.mD,A.f7,A.cQ,A.f8,A.aq,A.rj,A.qJ,A.jZ,A.kQ,A.dY,A.aL,A.ov,A.t2,A.qH,A.kA,A.c0,A.mt,A.bO,A.tg,A.cr,A.da,A.j_,A.bm,A.fL,A.cy,A.h0,A.bw,A.eD,A.eJ,A.dO,A.cC,A.k_,A.cE,A.eM,A.aR,A.f6,A.hK,A.kS,A.bq,A.b9,A.c2,A.qG,A.jX,A.qx,A.ci,A.dm,A.b8,A.f1,A.f2,A.f0,A.mP,A.mQ,A.fK,A.nC,A.nF,A.jz,A.h1,A.eB,A.dh,A.bX,A.iy,A.oX,A.p4,A.aV,A.bx,A.jS,A.qh,A.rD,A.lG,A.lI,A.rU,A.ld,A.fb,A.pv,A.aS,A.bF,A.c1,A.lf,A.t1,A.yA,A.ie])
p(J.k6,[J.hm,J.ho,J.hq,J.hp,J.hr,J.eQ,J.dj])
p(J.hq,[J.dl,J.D,A.eY,A.hz])
p(J.dl,[J.kC,J.e7,J.dk])
q(J.k8,A.hM)
q(J.q_,J.D)
p(J.eQ,[J.hn,J.k9])
p(A.m,[A.du,A.K,A.bi,A.a3,A.ha,A.cS,A.hZ,A.ij,A.lP,A.mH,A.d4])
p(A.du,[A.dK,A.iQ])
q(A.ia,A.dK)
q(A.i6,A.iQ)
p(A.bg,[A.jv,A.ju,A.k4,A.lq,A.y2,A.y4,A.u5,A.u4,A.xa,A.pm,A.po,A.uQ,A.uP,A.uX,A.v3,A.v6,A.t_,A.vV,A.vr,A.qp,A.wU,A.y6,A.yc,A.yd,A.xV,A.wM,A.nM,A.nO,A.nK,A.u_,A.oK,A.o5,A.of,A.pi,A.pj,A.pk,A.qA,A.t5,A.t6,A.rI,A.rJ,A.rK,A.rL,A.rM,A.rN,A.rP,A.o9,A.ob,A.o_,A.o2,A.xc,A.o7,A.qv,A.xY,A.oL,A.oM,A.oO,A.oW,A.t3,A.oQ,A.oS,A.oT,A.oP,A.vi,A.rW,A.rh,A.ri,A.rk,A.xh,A.pR,A.yg,A.yh,A.xj,A.ru,A.rt,A.rr,A.rp,A.rm,A.ow,A.ox,A.xq,A.xT,A.vu,A.vv,A.vF,A.r7,A.r8,A.r4,A.r5,A.r6,A.ra,A.rb,A.rc,A.r3,A.vG,A.qS,A.qT,A.r1,A.r_,A.qW,A.qV,A.qY,A.xd,A.xe,A.y7,A.nx,A.nv,A.nw,A.om,A.on,A.op,A.oq,A.oy,A.oz,A.oA,A.pu,A.pT,A.pU,A.pV,A.q8,A.rf,A.rw,A.nz,A.tC,A.tx,A.ty,A.tz,A.tU,A.tV,A.tX,A.tQ,A.tN,A.tR,A.uj,A.uk,A.ul,A.um,A.uo,A.uf,A.ug,A.ui,A.ol,A.ur,A.uA,A.uB,A.uw,A.ux,A.uL,A.uM,A.uJ,A.uK,A.pr,A.vb,A.v8,A.vf,A.vg,A.vl,A.vm,A.xl,A.qm,A.vz,A.vB,A.qF,A.uD,A.vQ,A.vJ,A.vK,A.vL,A.wF,A.wt,A.wu,A.wv,A.ww,A.wx,A.wy,A.wz,A.wa,A.wf,A.tc,A.td,A.wP,A.ts,A.tt,A.tu,A.x7,A.qM,A.qP,A.qO,A.qR,A.qN,A.qQ,A.x_,A.x0,A.x1,A.x2,A.x3,A.x4,A.nH,A.nG,A.ot,A.oE,A.oF,A.oG,A.oB,A.oC,A.oD,A.uH,A.xs,A.yb,A.ya,A.p_,A.p0,A.p1,A.p2,A.p3,A.oY,A.oZ,A.p9,A.p8,A.p6,A.pa,A.pd,A.pb,A.qj,A.qi,A.rH,A.rF,A.rG,A.tp,A.tq,A.tr,A.oo,A.q3,A.q4,A.q5,A.q6,A.q7,A.px,A.pw,A.py,A.pA,A.pC,A.pz,A.pQ,A.uC])
p(A.jv,[A.ub,A.ou,A.q0,A.y3,A.xb,A.xr,A.pn,A.uR,A.uY,A.v4,A.v7,A.vc,A.qf,A.qr,A.vq,A.tm,A.tl,A.o8,A.oa,A.oc,A.nZ,A.qw,A.oN,A.nW,A.xi,A.oR,A.rX,A.ro,A.xX,A.rv,A.xt,A.xu,A.xv,A.xG,A.xM,A.xN,A.xO,A.xP,A.xQ,A.xR,A.xS,A.xw,A.xx,A.xy,A.xz,A.xA,A.xB,A.xC,A.xD,A.xE,A.xF,A.xH,A.xI,A.xJ,A.xK,A.xL,A.tW,A.un,A.vM,A.pt,A.nE,A.nJ,A.pc,A.rE,A.pB])
q(A.cw,A.i6)
p(A.ad,[A.cG,A.cY,A.ka,A.lB,A.kR,A.m8,A.hF,A.ht,A.jf,A.bI,A.hY,A.lA,A.ck,A.jy,A.ix,A.eV])
q(A.fh,A.T)
q(A.c9,A.fh)
p(A.ju,[A.y9,A.u6,A.u7,A.wI,A.pp,A.uS,A.v_,A.uZ,A.uW,A.uU,A.uT,A.v2,A.v1,A.v0,A.v5,A.t0,A.wH,A.wG,A.ua,A.u9,A.vR,A.vE,A.vU,A.xo,A.wX,A.wW,A.wL,A.u3,A.u2,A.u1,A.u0,A.xm,A.xn,A.qu,A.og,A.nV,A.rx,A.o3,A.rs,A.rq,A.vt,A.vs,A.r9,A.rd,A.uF,A.uG,A.qU,A.r2,A.qZ,A.r0,A.qX,A.wJ,A.wK,A.ny,A.nA,A.nB,A.tB,A.tA,A.tw,A.tv,A.tE,A.tD,A.tK,A.tL,A.tF,A.tG,A.tH,A.tI,A.tJ,A.tM,A.tY,A.tT,A.tZ,A.tS,A.tP,A.tO,A.ue,A.uh,A.ok,A.uq,A.up,A.uz,A.uy,A.uv,A.uu,A.ut,A.uI,A.vW,A.va,A.v9,A.ve,A.vd,A.vk,A.vj,A.qk,A.ql,A.vD,A.vC,A.vy,A.vA,A.vx,A.vw,A.qC,A.qD,A.qE,A.uE,A.vP,A.vO,A.vI,A.vN,A.vH,A.w2,A.w5,A.w1,A.w3,A.vZ,A.w_,A.vX,A.w4,A.wE,A.wC,A.wD,A.w0,A.vY,A.ws,A.wr,A.wq,A.wp,A.wo,A.wn,A.wm,A.wA,A.wB,A.wh,A.wi,A.wj,A.wk,A.w9,A.wb,A.wc,A.w8,A.we,A.wd,A.w7,A.w6,A.wg,A.wl,A.wO,A.wN,A.x6,A.x5,A.ps,A.qy,A.nI,A.os,A.uO,A.uN,A.p7,A.p5,A.vT,A.pP,A.pD,A.pK,A.pL,A.pM,A.pN,A.pI,A.pJ,A.pE,A.pF,A.pG,A.pH,A.pO,A.vh])
p(A.K,[A.z,A.dM,A.aW,A.cI,A.aC,A.ii])
p(A.z,[A.e3,A.E,A.cN,A.mp])
q(A.dL,A.bi)
q(A.eC,A.cS)
p(A.br,[A.fq,A.eh,A.dx])
q(A.A,A.fq)
p(A.eh,[A.bQ,A.ei])
p(A.dx,[A.b2,A.ej,A.iu])
q(A.fw,A.eU)
q(A.d_,A.fw)
q(A.h3,A.d_)
q(A.i,A.h2)
p(A.cR,[A.h4,A.iz])
q(A.h5,A.h4)
q(A.eO,A.k4)
q(A.hC,A.cY)
p(A.lq,[A.lj,A.ex])
p(A.a5,[A.bz,A.eb,A.mo])
p(A.bz,[A.hs,A.ik])
p(A.hz,[A.kl,A.b_])
p(A.b_,[A.io,A.iq])
q(A.ip,A.io)
q(A.hy,A.ip)
q(A.ir,A.iq)
q(A.bA,A.ir)
p(A.hy,[A.km,A.kn])
p(A.bA,[A.ko,A.kp,A.kq,A.ks,A.hA,A.hB,A.dT])
q(A.fu,A.m8)
p(A.aH,[A.fs,A.e2,A.ib,A.il,A.id])
q(A.dv,A.fs)
q(A.aM,A.dv)
q(A.d1,A.fj)
q(A.d0,A.d1)
q(A.i3,A.i5)
p(A.fk,[A.c3,A.iE])
q(A.dt,A.fr)
p(A.d2,[A.c4,A.i9])
q(A.im,A.dt)
q(A.mC,A.iP)
q(A.fo,A.eb)
p(A.iz,[A.ed,A.c5])
p(A.cx,[A.df,A.fR,A.kb])
p(A.df,[A.je,A.kd,A.lF])
p(A.jF,[A.wR,A.wQ,A.jj,A.nY,A.q2,A.q1,A.to,A.tn])
p(A.wR,[A.nU,A.qa])
p(A.wQ,[A.nT,A.q9])
q(A.lW,A.o6)
q(A.kc,A.ht)
q(A.vo,A.vp)
p(A.bI,[A.f_,A.k2])
q(A.m_,A.iM)
p(A.e,[A.o,A.af,A.aZ,A.fQ,A.it,A.X,A.k,A.bK,A.iv])
p(A.o,[A.li,A.jn,A.jq,A.js,A.jD,A.jI,A.jT,A.kr,A.kU,A.lk,A.lv,A.ly,A.kV,A.kZ,A.l3,A.l4,A.l8,A.l9,A.fU,A.hh,A.mX,A.n4,A.n5,A.n6,A.n7,A.n8,A.n9,A.ne,A.ng,A.nl,A.c,A.nh,A.nj,A.mZ,A.iU,A.nb,A.nc,A.iX,A.no,A.ni,A.mY,A.n0,A.n2,A.na,A.nm,A.ep,A.nn,A.kG,A.jM,A.lp])
p(A.li,[A.fW,A.h_,A.fM,A.jb,A.fP,A.aY,A.cv,A.j3,A.j5,A.lu,A.j9,A.jd,A.j2,A.jw,A.j6,A.j8,A.ja,A.fe,A.j4,A.a6,A.j7,A.kH,A.is,A.f4,A.ma,A.mv,A.j0,A.lN,A.jt,A.jA,A.jK,A.jL,A.jP,A.mF,A.mu,A.hg,A.k0,A.lZ,A.k1,A.k5,A.lM,A.mn,A.mS,A.k7,A.kg,A.kh,A.ki,A.mg,A.kv,A.m9,A.kx,A.mm,A.mk,A.kB,A.lz,A.lJ,A.lK,A.kI,A.kJ,A.e0,A.kj,A.kL,A.kK,A.jC,A.jB,A.jW,A.jY,A.dQ,A.cO,A.dr,A.fc])
p(A.af,[A.lg,A.dn,A.ay])
p(A.lg,[A.ft,A.dG,A.eu,A.ev,A.dS,A.f3,A.er,A.es,A.db,A.dd,A.eA,A.eE,A.eG,A.eH,A.eK,A.eL,A.eT,A.eZ,A.dp,A.fg,A.fi,A.eF,A.dN,A.dX])
p(A.M,[A.iG,A.i2,A.lS,A.i1,A.mE,A.mr,A.hI,A.iF,A.lL,A.i0,A.lO,A.lY,A.i8,A.ic,A.mc,A.mh,A.mi,A.ml,A.ms,A.mw,A.iA,A.mM,A.mR,A.mb,A.md,A.mU])
p(A.us,[A.rQ,A.rS,A.cd,A.nu,A.dJ,A.jo,A.jr,A.hP,A.hQ,A.jJ,A.oV,A.jU,A.pg,A.rz,A.rB,A.rA,A.bv,A.cl,A.lw,A.ff,A.ta,A.oi,A.oh,A.o1,A.qn,A.oH,A.qo,A.pl,A.ls,A.he,A.qb,A.rO,A.ji,A.cF,A.hN,A.fm,A.cs,A.dR,A.hG,A.dW,A.bU,A.dg,A.dV,A.hf])
q(A.ih,A.fN)
p(A.aZ,[A.k3,A.hk,A.eN,A.h6,A.hc,A.hd,A.hi,A.hD,A.hL,A.hO])
q(A.fO,A.k3)
q(A.f9,A.jn)
q(A.kW,A.jq)
q(A.kX,A.js)
q(A.kY,A.jD)
q(A.l_,A.jI)
q(A.l0,A.jT)
q(A.l1,A.kr)
q(A.l2,A.kU)
q(A.l5,A.lk)
q(A.l7,A.lv)
q(A.la,A.ly)
q(A.l6,A.jc)
q(A.kN,A.c8)
q(A.jl,A.jk)
q(A.ey,A.e2)
q(A.kM,A.fS)
p(A.o0,[A.kO,A.hU])
q(A.ll,A.hU)
q(A.fX,A.U)
q(A.j1,A.lR)
q(A.lX,A.j1)
q(A.fZ,A.lX)
p(A.bW,[A.m1,A.h7,A.m3,A.mA,A.m5])
q(A.m2,A.m1)
q(A.jH,A.m2)
q(A.m4,A.m3)
q(A.bV,A.m4)
q(A.mB,A.mA)
q(A.kP,A.mB)
p(A.C,[A.hx,A.hu,A.fT])
q(A.eX,A.hx)
p(A.eX,[A.lV,A.jG,A.me,A.iw])
q(A.c7,A.h7)
q(A.eS,A.hu)
p(A.eS,[A.mx,A.lt])
q(A.i7,A.mT)
p(A.fv,[A.m7,A.my])
q(A.ln,A.mK)
q(A.l,A.ln)
p(A.fT,[A.hj,A.hT,A.lh])
q(A.kf,A.eR)
q(A.e8,A.kf)
p(A.dY,[A.cP,A.dq])
q(A.dZ,A.mE)
q(A.eP,A.t2)
p(A.eP,[A.kD,A.lE,A.lH])
q(A.mz,A.mU)
q(A.jR,A.ld)
p(A.fb,[A.fn,A.le])
q(A.fa,A.lf)
q(A.cT,A.le)
q(A.lm,A.fa)
q(A.m6,A.id)
s(A.fh,A.co)
s(A.iQ,A.T)
s(A.io,A.T)
s(A.ip,A.av)
s(A.iq,A.T)
s(A.ir,A.av)
s(A.dt,A.i4)
s(A.fw,A.iL)
s(A.lX,A.jx)
s(A.m1,A.cK)
s(A.m2,A.cD)
s(A.m3,A.cK)
s(A.m4,A.cD)
s(A.mA,A.cK)
s(A.mB,A.cD)
s(A.mT,A.ud)
s(A.mK,A.lo)
s(A.lR,A.kT)
r(A.eX,A.bp)
r(A.eS,A.bp)
s(A.mE,A.dU)
s(A.mU,A.dU)})()
var v={G:typeof self!="undefined"?self:globalThis,typeUniverse:{eC:new Map(),tR:{},eT:{},tPV:{},sEA:[]},mangledGlobalNames:{h:"int",v:"double",bd:"num",b:"String",y:"bool",aa:"Null",q:"List",u:"Object",L:"Map",a4:"JSObject"},mangledNames:{},types:["~()","e({size:cd})","~(b)","e(ac,aL)","~(a4)","y(b)","y(bw)","aa(a4)","aa()","~(y)","~(u)","b(@)","y(c0)","aa(u,ba)","~(u?)","~(C)","~(bU)","aa(u)","h()","~(b9)","y(aS)","y(c2)","b(cf)","b(b)","aa(@)","~(@)","~(u,ba)","h(bm,bm)","+id,name,snapshot(b,b,b9?)(aV)","y(bO)","~(~())","ae<~>()","aa(eD)","ah(b)","@()","b(aV)","~(b,u?)","y(bm)","L<b,@>(bq)","u?(u?)","y(a4)","b(W<b,b>)","aa(aq)","L<b,@>(aR)","@(@)","y(u?)","v(v)","y(bq)","h(@,@)","aa(~)","e(ac)","~(u?,u?)","h(u?)","ae<aq>(aq)","aq/(b?)","~(h)","y(u?,u?)","aR(@)","y(aV)","aq(~)","y(rl)","aa(~())","b(b?)","~(u?{url:b?})","dp(ac,aL)","db(ac,aL)","dd(ac,aL)","f4(ac,aL,e)","aa(ac,aL)","b?/(b?)","b?(b?,cQ)","de(h,C?)","bX(bO)","c0(bO)","C?(C?)","ci(bq)","W<b,b>(b,b)","cr(@)","0&(ac,aL)","h(c7,c7)","+(a4,a4)()","cy(@)","L<b,@>(cy)","aR(aR)","eJ(@)","cE(@)","L<b,@>(cE)","v(@)","~(b,~(a4))","~(b,L<b,u?>,y)","~(b?)","~(b,b,u?)","@(@,b)","ae<~>([b?])","b()","~(b,b)","eW()","~(q<h>)","y(cs)","~(kk<q<h>>)","+display,history,id,name,suffix,value(b,q<v>,b,b,b,v)(aV)","h(+display,history,id,name,suffix,value(b,q<v>,b,b,b,v),+display,history,id,name,suffix,value(b,q<v>,b,b,b,v))","+(b,q<v>)(+display,history,id,name,suffix,value(b,q<v>,b,b,b,v))","aa(b,b[u?])","L<b,~(a4)>({onChange:~(0^)?,onClick:~()?,onInput:~(0^)?})<u?>","ae<~>(b)","h(b)","y(b,b)","ah(cg)","aa(q<cC>)","aa(eM)","y(b?)","q<bw>()","dE(dF)","ae<~>(dW,di)","@(b)","bw(@)","dO(@)","c2(@)","da(@)","b6()","f6()","ae<~>(b,y)","ae<~>(b,b,u?)","ae<~>(y)","cg(ah)","aV(b)","b(bX)","e6(cn)","yF()","yG()","h(dg)","y(bx)","h(bx,bx)","W<b,q<b>>(b,@)","y(W<b,q<b>>)","b(W<b,q<b>>)","ae<~>(b{budgetMs:v?,panicMs:v?,releaseMs:v?})","~(h,h,h)","b?()","h(bF)","0&(b,h?)","u(bF)","u(aS)","h(aS,aS)","q<bF>(W<u,q<aS>>)","cT()","L<b,b>(L<b,b>,b)","~(@,@)","~(u[ba?])","~(h,@)","q<b>()","q<b>(b,q<b>)","aa(@,ba)","h(C,C)","aq/(ac,aq,f7,f8{extra:u?,redirectHistory:q<aq>?})","0^(0^,0^)<bd>","L<b,@>(cr)"],interceptorsByTag:null,leafTags:null,arrayRti:Symbol("$ti"),rttc:{"2;":(a,b)=>c=>c instanceof A.A&&a.b(c.a)&&b.b(c.b),"3;":(a,b,c)=>d=>d instanceof A.bQ&&a.b(d.a)&&b.b(d.b)&&c.b(d.c),"3;id,name,snapshot":(a,b,c)=>d=>d instanceof A.ei&&a.b(d.a)&&b.b(d.b)&&c.b(d.c),"4;":a=>b=>b instanceof A.b2&&A.zr(a,b.a),"5;":a=>b=>b instanceof A.ej&&A.zr(a,b.a),"6;display,history,id,name,suffix,value":a=>b=>b instanceof A.iu&&A.zr(a,b.a)}}
A.FL(v.typeUniverse,JSON.parse('{"dk":"dl","kC":"dl","e7":"dl","It":"eY","D":{"q":["1"],"K":["1"],"a4":[],"m":["1"]},"hm":{"y":[],"ag":[]},"ho":{"aa":[],"ag":[]},"hq":{"a4":[]},"dl":{"a4":[]},"k8":{"hM":[]},"q_":{"D":["1"],"q":["1"],"K":["1"],"a4":[],"m":["1"]},"dI":{"ab":["1"]},"eQ":{"v":[],"bd":[],"ax":["bd"]},"hn":{"v":[],"h":[],"bd":[],"ax":["bd"],"ag":[]},"k9":{"v":[],"bd":[],"ax":["bd"],"ag":[]},"dj":{"b":[],"ax":["b"],"qI":[],"ag":[]},"du":{"m":["2"]},"fY":{"ab":["2"]},"dK":{"du":["1","2"],"m":["2"],"m.E":"2"},"ia":{"dK":["1","2"],"du":["1","2"],"K":["2"],"m":["2"],"m.E":"2"},"i6":{"T":["2"],"q":["2"],"du":["1","2"],"K":["2"],"m":["2"]},"cw":{"i6":["1","2"],"T":["2"],"q":["2"],"du":["1","2"],"K":["2"],"m":["2"],"T.E":"2","m.E":"2"},"cG":{"ad":[]},"c9":{"T":["h"],"co":["h"],"q":["h"],"K":["h"],"m":["h"],"T.E":"h","co.E":"h"},"K":{"m":["1"]},"z":{"K":["1"],"m":["1"]},"e3":{"z":["1"],"K":["1"],"m":["1"],"m.E":"1","z.E":"1"},"aw":{"ab":["1"]},"bi":{"m":["2"],"m.E":"2"},"dL":{"bi":["1","2"],"K":["2"],"m":["2"],"m.E":"2"},"hw":{"ab":["2"]},"E":{"z":["2"],"K":["2"],"m":["2"],"m.E":"2","z.E":"2"},"a3":{"m":["1"],"m.E":"1"},"e9":{"ab":["1"]},"ha":{"m":["2"],"m.E":"2"},"hb":{"ab":["2"]},"cS":{"m":["1"],"m.E":"1"},"eC":{"cS":["1"],"K":["1"],"m":["1"],"m.E":"1"},"hR":{"ab":["1"]},"dM":{"K":["1"],"m":["1"],"m.E":"1"},"h8":{"ab":["1"]},"hZ":{"m":["1"],"m.E":"1"},"i_":{"ab":["1"]},"fh":{"T":["1"],"co":["1"],"q":["1"],"K":["1"],"m":["1"]},"cN":{"z":["1"],"K":["1"],"m":["1"],"m.E":"1","z.E":"1"},"A":{"fq":[],"br":[]},"bQ":{"eh":[],"br":[]},"ei":{"eh":[],"br":[]},"b2":{"dx":[],"br":[]},"ej":{"dx":[],"br":[]},"iu":{"dx":[],"br":[]},"h3":{"d_":["1","2"],"fw":["1","2"],"eU":["1","2"],"iL":["1","2"],"L":["1","2"]},"h2":{"L":["1","2"]},"i":{"h2":["1","2"],"L":["1","2"]},"ij":{"m":["1"],"m.E":"1"},"ee":{"ab":["1"]},"h4":{"cR":["1"],"e1":["1"],"K":["1"],"m":["1"]},"h5":{"h4":["1"],"cR":["1"],"e1":["1"],"K":["1"],"m":["1"]},"k4":{"bg":[],"cB":[]},"eO":{"bg":[],"cB":[]},"hC":{"cY":[],"ad":[]},"ka":{"ad":[]},"lB":{"ad":[]},"ku":{"aj":[]},"iB":{"ba":[]},"bg":{"cB":[]},"ju":{"bg":[],"cB":[]},"jv":{"bg":[],"cB":[]},"lq":{"bg":[],"cB":[]},"lj":{"bg":[],"cB":[]},"ex":{"bg":[],"cB":[]},"kR":{"ad":[]},"bz":{"a5":["1","2"],"qc":["1","2"],"L":["1","2"],"a5.K":"1","a5.V":"2"},"aW":{"K":["1"],"m":["1"],"m.E":"1"},"hv":{"ab":["1"]},"cI":{"K":["1"],"m":["1"],"m.E":"1"},"bh":{"ab":["1"]},"aC":{"K":["W<1,2>"],"m":["W<1,2>"],"m.E":"W<1,2>"},"cH":{"ab":["W<1,2>"]},"hs":{"bz":["1","2"],"a5":["1","2"],"qc":["1","2"],"L":["1","2"],"a5.K":"1","a5.V":"2"},"fq":{"br":[]},"eh":{"br":[]},"dx":{"br":[]},"dP":{"EG":[],"qI":[]},"fp":{"hJ":[],"cf":[]},"lP":{"m":["hJ"],"m.E":"hJ"},"ds":{"ab":["hJ"]},"hV":{"cf":[]},"mH":{"m":["cf"],"m.E":"cf"},"mI":{"ab":["cf"]},"eY":{"a4":[],"yv":[],"ag":[]},"hz":{"a4":[]},"kl":{"yw":[],"a4":[],"ag":[]},"b_":{"by":["1"],"a4":[]},"hy":{"T":["v"],"b_":["v"],"q":["v"],"by":["v"],"K":["v"],"a4":[],"m":["v"],"av":["v"]},"bA":{"T":["h"],"b_":["h"],"q":["h"],"by":["h"],"K":["h"],"a4":[],"m":["h"],"av":["h"]},"km":{"pe":[],"T":["v"],"b_":["v"],"q":["v"],"by":["v"],"K":["v"],"a4":[],"m":["v"],"av":["v"],"ag":[],"T.E":"v","av.E":"v"},"kn":{"pf":[],"T":["v"],"b_":["v"],"q":["v"],"by":["v"],"K":["v"],"a4":[],"m":["v"],"av":["v"],"ag":[],"T.E":"v","av.E":"v"},"ko":{"bA":[],"pX":[],"T":["h"],"b_":["h"],"q":["h"],"by":["h"],"K":["h"],"a4":[],"m":["h"],"av":["h"],"ag":[],"T.E":"h","av.E":"h"},"kp":{"bA":[],"pY":[],"T":["h"],"b_":["h"],"q":["h"],"by":["h"],"K":["h"],"a4":[],"m":["h"],"av":["h"],"ag":[],"T.E":"h","av.E":"h"},"kq":{"bA":[],"pZ":[],"T":["h"],"b_":["h"],"q":["h"],"by":["h"],"K":["h"],"a4":[],"m":["h"],"av":["h"],"ag":[],"T.E":"h","av.E":"h"},"ks":{"bA":[],"th":[],"T":["h"],"b_":["h"],"q":["h"],"by":["h"],"K":["h"],"a4":[],"m":["h"],"av":["h"],"ag":[],"T.E":"h","av.E":"h"},"hA":{"bA":[],"ti":[],"T":["h"],"b_":["h"],"q":["h"],"by":["h"],"K":["h"],"a4":[],"m":["h"],"av":["h"],"ag":[],"T.E":"h","av.E":"h"},"hB":{"bA":[],"tj":[],"T":["h"],"b_":["h"],"q":["h"],"by":["h"],"K":["h"],"a4":[],"m":["h"],"av":["h"],"ag":[],"T.E":"h","av.E":"h"},"dT":{"bA":[],"hX":[],"T":["h"],"b_":["h"],"q":["h"],"by":["h"],"K":["h"],"a4":[],"m":["h"],"av":["h"],"ag":[],"T.E":"h","av.E":"h"},"mN":{"B9":[]},"m8":{"ad":[]},"fu":{"cY":[],"ad":[]},"aG":{"ad":[]},"a_":{"ae":["1"]},"kk":{"fd":["1"]},"mL":{"F1":[]},"d5":{"ab":["1"]},"d4":{"m":["1"],"m.E":"1"},"aM":{"dv":["1"],"fs":["1"],"aH":["1"],"aH.T":"1"},"d0":{"d1":["1"],"fj":["1"],"bj":["1"],"bP":["1"]},"i5":{"fd":["1"],"iD":["1"],"bP":["1"]},"i3":{"i5":["1"],"fd":["1"],"iD":["1"],"bP":["1"]},"e5":{"aj":[]},"hF":{"ad":[]},"c3":{"fk":["1"]},"iE":{"fk":["1"]},"e2":{"aH":["1"]},"fr":{"fd":["1"],"iD":["1"],"bP":["1"]},"dt":{"i4":["1"],"fr":["1"],"fd":["1"],"iD":["1"],"bP":["1"]},"dv":{"fs":["1"],"aH":["1"],"aH.T":"1"},"d1":{"fj":["1"],"bj":["1"],"bP":["1"]},"fj":{"bj":["1"],"bP":["1"]},"fs":{"aH":["1"]},"c4":{"d2":["1"]},"i9":{"d2":["@"]},"m0":{"d2":["@"]},"fl":{"bj":["1"]},"ib":{"aH":["1"],"aH.T":"1"},"il":{"aH":["1"],"aH.T":"1"},"im":{"dt":["1"],"i4":["1"],"fr":["1"],"kk":["1"],"fd":["1"],"iD":["1"],"bP":["1"]},"iP":{"Bh":[]},"mC":{"iP":[],"Bh":[]},"eb":{"a5":["1","2"],"L":["1","2"],"a5.K":"1","a5.V":"2"},"fo":{"eb":["1","2"],"a5":["1","2"],"L":["1","2"],"a5.K":"1","a5.V":"2"},"ii":{"K":["1"],"m":["1"],"m.E":"1"},"ec":{"ab":["1"]},"ik":{"bz":["1","2"],"a5":["1","2"],"qc":["1","2"],"L":["1","2"],"a5.K":"1","a5.V":"2"},"ed":{"cR":["1"],"e1":["1"],"K":["1"],"m":["1"]},"d3":{"ab":["1"]},"c5":{"cR":["1"],"AA":["1"],"e1":["1"],"K":["1"],"m":["1"]},"ef":{"ab":["1"]},"T":{"q":["1"],"K":["1"],"m":["1"]},"a5":{"L":["1","2"]},"eU":{"L":["1","2"]},"d_":{"fw":["1","2"],"eU":["1","2"],"iL":["1","2"],"L":["1","2"]},"cR":{"e1":["1"],"K":["1"],"m":["1"]},"iz":{"cR":["1"],"e1":["1"],"K":["1"],"m":["1"]},"df":{"cx":["b","q<h>"]},"mo":{"a5":["b","@"],"L":["b","@"],"a5.K":"b","a5.V":"@"},"mp":{"z":["b"],"K":["b"],"m":["b"],"m.E":"b","z.E":"b"},"je":{"df":[],"cx":["b","q<h>"]},"fR":{"cx":["q<h>","b"]},"ht":{"ad":[]},"kc":{"ad":[]},"kb":{"cx":["u?","b"]},"kd":{"df":[],"cx":["b","q<h>"]},"lF":{"df":[],"cx":["b","q<h>"]},"b6":{"ax":["b6"]},"v":{"bd":[],"ax":["bd"]},"ca":{"ax":["ca"]},"h":{"bd":[],"ax":["bd"]},"q":{"K":["1"],"m":["1"]},"bd":{"ax":["bd"]},"hJ":{"cf":[]},"b":{"ax":["b"],"qI":[]},"jf":{"ad":[]},"cY":{"ad":[]},"bI":{"ad":[]},"f_":{"ad":[]},"k2":{"ad":[]},"hY":{"ad":[]},"lA":{"ad":[]},"ck":{"ad":[]},"jy":{"ad":[]},"kw":{"ad":[]},"hS":{"ad":[]},"dw":{"aj":[]},"bn":{"aj":[]},"mJ":{"ba":[]},"aI":{"EZ":[]},"iM":{"lC":[]},"bR":{"lC":[]},"m_":{"lC":[]},"kt":{"aj":[]},"fW":{"o":[],"e":[]},"h_":{"o":[],"e":[]},"fM":{"o":[],"e":[]},"jb":{"o":[],"e":[]},"ft":{"af":[],"e":[]},"iG":{"M":["ft"],"M.T":"ft"},"fP":{"o":[],"e":[]},"aY":{"o":[],"e":[]},"cv":{"o":[],"e":[]},"j3":{"o":[],"e":[]},"j5":{"o":[],"e":[]},"lu":{"o":[],"e":[]},"j9":{"o":[],"e":[]},"jd":{"o":[],"e":[]},"j2":{"o":[],"e":[]},"jw":{"o":[],"e":[]},"j6":{"o":[],"e":[]},"j8":{"o":[],"e":[]},"ja":{"o":[],"e":[]},"dG":{"af":[],"e":[]},"i2":{"M":["dG"],"M.T":"dG"},"eu":{"af":[],"e":[]},"lS":{"M":["eu"],"M.T":"eu"},"fe":{"o":[],"e":[]},"j4":{"o":[],"e":[]},"ev":{"af":[],"e":[]},"i1":{"M":["ev"],"M.T":"ev"},"a6":{"o":[],"e":[]},"ih":{"fN":[]},"jn":{"o":[],"e":[]},"jq":{"o":[],"e":[]},"js":{"o":[],"e":[]},"jD":{"o":[],"e":[]},"jI":{"o":[],"e":[]},"jT":{"o":[],"e":[]},"kr":{"o":[],"e":[]},"kU":{"o":[],"e":[]},"lk":{"o":[],"e":[]},"lv":{"o":[],"e":[]},"ly":{"o":[],"e":[]},"fO":{"aZ":[],"e":[]},"li":{"o":[],"e":[]},"lg":{"af":[],"e":[]},"k3":{"aZ":[],"e":[]},"j7":{"o":[],"e":[]},"kV":{"o":[],"e":[]},"f9":{"o":[],"e":[]},"kW":{"o":[],"e":[]},"kX":{"o":[],"e":[]},"kY":{"o":[],"e":[]},"kZ":{"o":[],"e":[]},"l3":{"o":[],"e":[]},"l_":{"o":[],"e":[]},"l0":{"o":[],"e":[]},"l1":{"o":[],"e":[]},"l2":{"o":[],"e":[]},"l4":{"o":[],"e":[]},"l5":{"o":[],"e":[]},"l7":{"o":[],"e":[]},"l8":{"o":[],"e":[]},"l9":{"o":[],"e":[]},"la":{"o":[],"e":[]},"l6":{"jc":[]},"U":{"L":["2","3"]},"kN":{"aj":[]},"jk":{"Ai":[]},"jl":{"Ai":[]},"ey":{"e2":["q<h>"],"aH":["q<h>"],"aH.T":"q<h>","e2.T":"q<h>"},"c8":{"aj":[]},"kM":{"fS":[]},"ll":{"hU":[]},"fX":{"U":["b","b","1"],"L":["b","1"],"U.K":"b","U.V":"1","U.C":"b"},"fZ":{"j1":[]},"bW":{"f5":[]},"jH":{"cK":[],"cD":[],"bW":[],"AY":[],"f5":[]},"h7":{"bW":[],"yS":[],"f5":[]},"bV":{"cK":[],"cD":[],"bW":[],"AZ":[],"f5":[]},"kP":{"cK":[],"cD":[],"bW":[],"f5":[]},"fU":{"o":[],"e":[]},"c7":{"bW":[],"yS":[],"f5":[]},"hh":{"o":[],"e":[]},"fQ":{"e":[]},"lV":{"bp":[],"C":[],"ac":[]},"mX":{"o":[],"e":[]},"n4":{"o":[],"e":[]},"n5":{"o":[],"e":[]},"n6":{"o":[],"e":[]},"n7":{"o":[],"e":[]},"n8":{"o":[],"e":[]},"n9":{"o":[],"e":[]},"ne":{"o":[],"e":[]},"ng":{"o":[],"e":[]},"nl":{"o":[],"e":[]},"c":{"o":[],"e":[]},"nh":{"o":[],"e":[]},"nj":{"o":[],"e":[]},"mZ":{"o":[],"e":[]},"iU":{"o":[],"e":[]},"nb":{"o":[],"e":[]},"nc":{"o":[],"e":[]},"iX":{"o":[],"e":[]},"no":{"o":[],"e":[]},"ni":{"o":[],"e":[]},"mY":{"o":[],"e":[]},"n0":{"o":[],"e":[]},"n2":{"o":[],"e":[]},"na":{"o":[],"e":[]},"nm":{"o":[],"e":[]},"ep":{"o":[],"e":[]},"nn":{"o":[],"e":[]},"kG":{"o":[],"e":[]},"it":{"e":[]},"mx":{"bp":[],"C":[],"ac":[]},"m5":{"bW":[],"f5":[]},"i7":{"DD":[]},"lQ":{"EW":[]},"fv":{"yX":[]},"m7":{"yX":[]},"my":{"yX":[]},"l":{"ln":[]},"cm":{"ae":["1"]},"BS":{"aZ":[],"X":[],"e":[]},"C":{"ac":[]},"aZ":{"e":[]},"hj":{"C":[],"ac":[]},"Iu":{"C":[],"ac":[]},"af":{"e":[]},"fT":{"C":[],"ac":[]},"X":{"e":[]},"jG":{"bp":[],"C":[],"ac":[]},"k":{"e":[]},"lt":{"bp":[],"C":[],"ac":[]},"bK":{"e":[]},"me":{"bp":[],"C":[],"ac":[]},"iv":{"e":[]},"iw":{"bp":[],"C":[],"ac":[]},"kf":{"eR":[]},"e8":{"eR":[]},"hu":{"C":[],"ac":[]},"hx":{"C":[],"ac":[]},"eX":{"bp":[],"C":[],"ac":[]},"eS":{"bp":[],"C":[],"ac":[]},"hT":{"C":[],"ac":[]},"o":{"e":[]},"lh":{"C":[],"ac":[]},"ix":{"ad":[]},"mD":{"aj":[]},"eV":{"ad":[]},"jM":{"o":[],"e":[]},"hk":{"aZ":[],"e":[]},"eN":{"aZ":[],"e":[]},"jZ":{"E4":[]},"kQ":{"EO":[]},"cP":{"dY":[]},"dq":{"dY":[]},"dn":{"af":[],"e":[]},"dZ":{"dU":["dn"],"M":["dn"],"M.T":"dn"},"kA":{"aj":[]},"kD":{"eP":[]},"lE":{"eP":[]},"lH":{"eP":[]},"dS":{"af":[],"e":[]},"f4":{"o":[],"e":[]},"f3":{"af":[],"e":[]},"kH":{"o":[],"e":[]},"mr":{"M":["dS"],"M.T":"dS"},"is":{"o":[],"e":[]},"ma":{"o":[],"e":[]},"mv":{"o":[],"e":[]},"hI":{"M":["f3"],"DT":[],"M.T":"f3"},"lp":{"o":[],"e":[]},"ay":{"af":[],"e":[]},"iF":{"M":["ay"],"M.T":"ay"},"er":{"af":[],"e":[]},"j0":{"o":[],"e":[]},"lL":{"M":["er"],"M.T":"er"},"es":{"af":[],"e":[]},"i0":{"M":["es"],"M.T":"es"},"db":{"af":[],"e":[]},"lO":{"M":["db"],"M.T":"db"},"lN":{"o":[],"e":[]},"jt":{"o":[],"e":[]},"dd":{"af":[],"e":[]},"lY":{"M":["dd"],"M.T":"dd"},"eA":{"af":[],"e":[]},"jA":{"o":[],"e":[]},"i8":{"M":["eA"],"M.T":"eA"},"jK":{"o":[],"e":[]},"eE":{"af":[],"e":[]},"jL":{"o":[],"e":[]},"ic":{"M":["eE"],"M.T":"eE"},"jP":{"o":[],"e":[]},"eG":{"af":[],"e":[]},"mc":{"M":["eG"],"M.T":"eG"},"mF":{"o":[],"e":[]},"mu":{"o":[],"e":[]},"eH":{"af":[],"e":[]},"hg":{"o":[],"e":[]},"mh":{"M":["eH"],"M.T":"eH"},"eK":{"af":[],"e":[]},"mi":{"M":["eK"],"M.T":"eK"},"eL":{"af":[],"e":[]},"k0":{"o":[],"e":[]},"lZ":{"o":[],"e":[]},"ml":{"M":["eL"],"M.T":"eL"},"k1":{"o":[],"e":[]},"k5":{"o":[],"e":[]},"lM":{"o":[],"e":[]},"mn":{"o":[],"e":[]},"mS":{"o":[],"e":[]},"k7":{"o":[],"e":[]},"eT":{"af":[],"e":[]},"kg":{"o":[],"e":[]},"ms":{"M":["eT"],"M.T":"eT"},"kh":{"o":[],"e":[]},"ki":{"o":[],"e":[]},"mg":{"o":[],"e":[]},"eZ":{"af":[],"e":[]},"kv":{"o":[],"e":[]},"m9":{"o":[],"e":[]},"mw":{"M":["eZ"],"M.T":"eZ"},"kx":{"o":[],"e":[]},"mm":{"o":[],"e":[]},"mk":{"o":[],"e":[]},"kB":{"o":[],"e":[]},"dp":{"af":[],"e":[]},"iA":{"M":["dp"],"M.T":"dp"},"fg":{"af":[],"e":[]},"lz":{"o":[],"e":[]},"mM":{"M":["fg"],"M.T":"fg"},"fi":{"af":[],"e":[]},"lJ":{"o":[],"e":[]},"mR":{"M":["fi"],"M.T":"fi"},"lK":{"o":[],"e":[]},"jX":{"di":[],"pS":[]},"di":{"pS":[]},"ci":{"di":[],"Ea":[],"pS":[],"E8":[],"E7":[],"E5":[],"E6":[],"E9":[],"Eb":[]},"dm":{"aj":[]},"b8":{"aj":[]},"f1":{"aj":[]},"f2":{"aj":[]},"f0":{"aj":[]},"mP":{"yF":[]},"mQ":{"yG":[]},"h6":{"aZ":[],"e":[]},"eF":{"af":[],"e":[]},"mb":{"M":["eF"],"M.T":"eF"},"hc":{"aZ":[],"e":[]},"dN":{"af":[],"e":[]},"md":{"M":["dN"],"M.T":"dN"},"hd":{"aZ":[],"e":[]},"hi":{"aZ":[],"e":[]},"hD":{"aZ":[],"e":[]},"hL":{"aZ":[],"e":[]},"dX":{"af":[],"e":[]},"mz":{"dU":["dX"],"M":["dX"],"M.T":"dX"},"hO":{"aZ":[],"e":[]},"lG":{"DW":[]},"kI":{"o":[],"e":[]},"kJ":{"o":[],"e":[]},"e0":{"o":[],"e":[]},"kj":{"o":[],"e":[]},"kL":{"o":[],"e":[]},"kK":{"o":[],"e":[]},"jC":{"o":[],"e":[]},"jB":{"o":[],"e":[]},"jW":{"o":[],"e":[]},"jY":{"o":[],"e":[]},"dQ":{"o":[],"e":[]},"cO":{"o":[],"e":[]},"dr":{"o":[],"e":[]},"fc":{"o":[],"e":[]},"jR":{"c1":[],"ax":["c1"]},"fn":{"cT":[],"cj":[],"ax":["cj"]},"c1":{"ax":["c1"]},"ld":{"c1":[],"ax":["c1"]},"cj":{"ax":["cj"]},"le":{"cj":[],"ax":["cj"]},"lf":{"aj":[]},"fa":{"bn":[],"aj":[]},"fb":{"cj":[],"ax":["cj"]},"cT":{"cj":[],"ax":["cj"]},"lm":{"bn":[],"aj":[]},"id":{"aH":["1"],"aH.T":"1"},"m6":{"id":["1"],"aH":["1"],"aH.T":"1"},"ie":{"bj":["1"]},"pZ":{"q":["h"],"K":["h"],"m":["h"]},"hX":{"q":["h"],"K":["h"],"m":["h"]},"tj":{"q":["h"],"K":["h"],"m":["h"]},"pX":{"q":["h"],"K":["h"],"m":["h"]},"th":{"q":["h"],"K":["h"],"m":["h"]},"pY":{"q":["h"],"K":["h"],"m":["h"]},"ti":{"q":["h"],"K":["h"],"m":["h"]},"pe":{"q":["v"],"K":["v"],"m":["v"]},"pf":{"q":["v"],"K":["v"],"m":["v"]}}'))
A.FK(v.typeUniverse,JSON.parse('{"fh":1,"iQ":2,"b_":1,"d2":1,"iz":1,"jF":2,"lo":1}'))
var u={v:"\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\u03f6\x00\u0404\u03f4 \u03f4\u03f6\u01f6\u01f6\u03f6\u03fc\u01f4\u03ff\u03ff\u0584\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u05d4\u01f4\x00\u01f4\x00\u0504\u05c4\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u0400\x00\u0400\u0200\u03f7\u0200\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u03ff\u0200\u0200\u0200\u03f7\x00",s:" must not be greater than the number of characters in the file, ",E:"0 10px 15px -3px rgb(0 0 0 / 0.1), 0 4px 6px -4px rgb(0 0 0 / 0.1)",h:"1px solid color-mix(in srgb, var(--border) 64%, transparent)",l:"Cannot extract a file path from a URI with a fragment component",y:"Cannot extract a file path from a URI with a query component",j:"Cannot extract a non-Windows file path from a file URI with an authority",c:"Error handler must accept one Object or one Object and a StackTrace as arguments, and return a value of the returned future's type",x:"Text nodes cannot have children removed from them."}
var t=(function rtii(){var s=A.aJ
return{bm:s("@<~>"),kS:s("da"),ej:s("fK"),fS:s("cr"),al:s("j_"),eV:s("cs"),eS:s("dF"),oZ:s("ah"),cl:s("ah(b)"),o5:s("dG"),cC:s("fO"),n:s("aG"),k7:s("fQ"),df:s("c7"),gC:s("ac"),lo:s("yv"),fW:s("yw"),kj:s("fX<b>"),gS:s("c9"),bP:s("ax<@>"),aI:s("e"),o0:s("e({size:cd})"),ap:s("cy"),jd:s("h0"),x:s("bU"),mN:s("h1"),w:s("i<b,b>"),j:s("bw"),A:s("h6"),cs:s("b6"),J:s("X"),jS:s("ca"),gt:s("K<@>"),Q:s("C"),aC:s("eD"),B:s("ad"),lL:s("jO"),mA:s("aj"),e:s("bm"),hX:s("dN"),T:s("hc"),ii:s("bX"),ne:s("hd"),eR:s("bx"),d:s("aV"),pk:s("pe"),kI:s("pf"),lW:s("bn"),gF:s("bK"),gY:s("cB"),g7:s("ae<@>"),p8:s("ae<~>"),kN:s("eJ"),lP:s("cC"),fI:s("hi"),e_:s("dO"),fh:s("cD"),lF:s("di"),W:s("cd"),kL:s("k_"),l4:s("cE"),h6:s("eM"),p:s("aZ"),a3:s("hj"),hn:s("eN"),hj:s("hk"),m6:s("pX"),bW:s("pY"),jx:s("pZ"),bq:s("m<b>"),e7:s("m<@>"),fm:s("m<h>"),cP:s("D<fK>"),kk:s("D<dF>"),gf:s("D<fN>"),lZ:s("D<ah>"),ox:s("D<c7>"),i:s("D<e>"),eB:s("D<bw>"),il:s("D<C>"),a2:s("D<bm>"),k6:s("D<bx>"),bk:s("D<aV>"),iw:s("D<ae<~>>"),aw:s("D<cc>"),n5:s("D<cC>"),bb:s("D<D<u?>>"),O:s("D<a4>"),gm:s("D<L<b,b>>"),hf:s("D<u>"),y:s("D<+(b,q<v>)>"),m_:s("D<+(b,b)>"),kV:s("D<dY>"),ai:s("D<rl>"),E:s("D<cQ>"),g1:s("D<aq>"),cy:s("D<bq>"),s:s("D<b>"),I:s("D<cn>"),jm:s("D<bO>"),pg:s("D<aS>"),dg:s("D<bF>"),gk:s("D<v>"),dG:s("D<@>"),lC:s("D<h>"),fQ:s("D<aG?>"),mf:s("D<b?>"),u:s("D<~()>"),bE:s("ho"),m:s("a4"),k:s("dk"),dX:s("by<@>"),er:s("eR"),mv:s("aR"),fT:s("q<da>"),kT:s("q<e>"),iD:s("q<bw>"),jB:s("q<C>"),hg:s("q<bm>"),lT:s("q<bX>"),cm:s("q<aV>"),iM:s("q<cC>"),jP:s("q<dO>"),bi:s("q<+(b,q<v>)>"),nt:s("q<+display,history,id,name,suffix,value(b,q<v>,b,b,b,v)>"),hb:s("q<dY>"),jO:s("q<bq>"),h:s("q<b>"),et:s("q<c2>"),bd:s("q<v>"),_:s("q<@>"),L:s("q<h>"),eU:s("q<aS?>"),jb:s("dS"),gc:s("W<b,b>"),lO:s("W<u,q<aS>>"),cW:s("W<b,q<b>>"),ln:s("L<u,rl>"),f:s("L<b,b>"),P:s("L<b,@>"),av:s("L<@,@>"),G:s("L<b,u?>"),bO:s("E<b,ah>"),gQ:s("E<b,b>"),iZ:s("E<b,@>"),br:s("eW"),mV:s("cK"),o1:s("kk<q<h>>"),i_:s("cg"),aj:s("bA"),hD:s("dT"),a:s("aa"),K:s("u"),Y:s("hD"),eg:s("dU<@>"),nJ:s("Ix"),aK:s("+()"),pa:s("+display,history,id,name,suffix,value(b,q<v>,b,b,b,v)"),F:s("hJ"),bY:s("AY"),mj:s("AZ"),fX:s("bp"),e8:s("yS"),cD:s("kO"),bw:s("f6"),mo:s("hK"),U:s("hL"),fM:s("f7"),oN:s("rl"),dv:s("cQ"),b:s("aq"),fu:s("f8"),aT:s("aL"),nA:s("dn"),aJ:s("dZ"),e5:s("kS"),C:s("bq"),o:s("c0"),V:s("hO"),c:s("b9"),gi:s("e1<b>"),hq:s("c1"),hs:s("cj"),ol:s("cT"),l:s("ba"),mi:s("af"),ft:s("o"),hr:s("bj<bU>"),hL:s("hU"),N:s("b"),po:s("b(cf)"),d1:s("b(b)"),b7:s("cm<aq>"),e1:s("cm<~>"),oI:s("k"),aO:s("ay"),dO:s("cn"),dH:s("ag"),ha:s("B9"),do:s("cY"),hM:s("th"),mC:s("ti"),nn:s("tj"),ev:s("hX"),cx:s("e7"),ph:s("d_<b,b>"),R:s("lC"),mg:s("e8<a4>"),le:s("e8<b?>"),el:s("a3<bx>"),cF:s("a3<b>"),cA:s("a3<b?>"),lS:s("hZ<b>"),q:s("c2"),mn:s("bO"),am:s("c3<di>"),iq:s("c3<hX>"),ou:s("c3<~>"),oU:s("dt<q<h>>"),gX:s("m6<a4>"),h_:s("a_<di>"),jz:s("a_<hX>"),j_:s("a_<@>"),hy:s("a_<h>"),cU:s("a_<~>"),D:s("aS"),mp:s("fo<u?,u?>"),nR:s("bF"),e6:s("il<q<h>>"),pj:s("it"),cf:s("iv"),cH:s("iy"),gL:s("iC<u?>"),kP:s("d4<a4>"),b_:s("BS"),k4:s("y"),nx:s("y(bx)"),bD:s("y(a4)"),iW:s("y(u)"),dA:s("y(b)"),aP:s("y(aS)"),i7:s("y(b?)"),r:s("v"),z:s("@"),mY:s("@()"),mq:s("@(u)"),ng:s("@(u,ba)"),f5:s("@(b)"),S:s("h"),n2:s("bW?"),c_:s("C?"),gK:s("ae<aa>?"),gR:s("cC?"),mU:s("a4?"),ga:s("q<aR>?"),ja:s("q<aq>?"),g:s("q<@>?"),t:s("L<b,b>?"),oq:s("L<b,~(a4)>?"),X:s("u?"),dK:s("hK?"),an:s("e1<C>?"),fw:s("ba?"),ky:s("bj<bU>?"),nz:s("bj<b9>?"),jv:s("b?"),jt:s("b(cf)?"),nf:s("d2<@>?"),np:s("bE<@,@>?"),dd:s("aS?"),nF:s("mq?"),fU:s("y?"),jX:s("v?"),aV:s("h?"),jh:s("bd?"),Z:s("~()?"),bl:s("~(a4)?"),aD:s("~(u?{url:b?})?"),cZ:s("bd"),H:s("~"),M:s("~()"),p9:s("~(C)"),v:s("~(a4)"),nw:s("~(q<h>)"),i6:s("~(u)"),b9:s("~(u,ba)"),eF:s("~(b)"),lc:s("~(b,@)"),lt:s("~(h)")}})();(function constants(){var s=hunkHelpers.makeConstList
B.d4=J.k6.prototype
B.b=J.D.prototype
B.d6=J.hm.prototype
B.c=J.hn.prototype
B.e=J.eQ.prototype
B.a=J.dj.prototype
B.d7=J.dk.prototype
B.d8=J.hq.prototype
B.af=A.hA.prototype
B.G=A.dT.prototype
B.bi=J.kC.prototype
B.ao=J.e7.prototype
B.mg=new A.nu(0,"standard")
B.bL=new A.er(null)
B.X=new A.cs(0,"info")
B.r=new A.cs(1,"warning")
B.E=new A.cs(2,"critical")
B.F=new A.fL(18,10,50,50,15,200,90)
B.bM=new A.db(null)
B.bN=new A.j7(null)
B.au=new A.ah("All","",!1)
B.bT=new A.ah("critical","critical",!1)
B.bU=new A.ah("info","info",!1)
B.bV=new A.ah("warning","warning",!1)
B.y=new A.ta(5,"bottomRight")
B.bW=new A.jb(null)
B.A=new A.oi(1,"md")
B.o=new A.bv(0,"status")
B.a2=new A.cl(7,"info")
B.bX=new A.aY("Operator",B.o,B.a2,!1,!0,null)
B.T=new A.cl(4,"success")
B.bY=new A.aY("Admin",B.o,B.T,!0,!0,null)
B.U=new A.cl(6,"warning")
B.bZ=new A.aY("Viewer",B.o,B.U,!1,!0,null)
B.c_=new A.aY("Enabled",B.o,B.T,!1,!0,null)
B.c0=new A.aY("NORMAL",B.o,B.T,!1,!0,null)
B.bu=new A.cl(1,"offline")
B.c1=new A.aY("Disabled",B.o,B.bu,!1,!0,null)
B.c2=new A.aY("PRESSURE",B.o,B.U,!1,!0,null)
B.am=new A.cl(5,"error")
B.c3=new A.aY("PANIC",B.o,B.am,!1,!0,null)
B.c4=new A.aY("Destructive",B.o,B.U,!1,!0,null)
B.c5=new A.nT(!1,127)
B.c6=new A.nU(127)
B.c7=new A.ji(2,"head")
B.a7=new A.bv(1,"popular")
B.av=new A.bv(10,"outline")
B.a8=new A.bv(2,"recommended")
B.a9=new A.bv(3,"isNew")
B.aw=new A.bv(4,"primary")
B.aa=new A.bv(5,"secondary")
B.ax=new A.bv(6,"successSolid")
B.ay=new A.bv(7,"warningSolid")
B.az=new A.bv(8,"errorSolid")
B.aA=new A.bv(9,"infoSolid")
B.ca=new A.jj(!1)
B.c8=new A.fR(B.ca)
B.cb=new A.jj(!0)
B.c9=new A.fR(B.cb)
B.aB=new A.o1(1,"dark")
B.h=new A.jo(0,"sm")
B.v=new A.jo(1,"md")
B.aC=new A.dJ(0,"primary")
B.cc=new A.dJ(1,"secondary")
B.aD=new A.dJ(2,"outline")
B.cd=new A.dJ(3,"ghost")
B.aE=new A.dJ(4,"destructive")
B.cs=new A.ib(A.aJ("ib<q<h>>"))
B.ce=new A.ey(B.cs)
B.cf=new A.eO(A.I6(),A.aJ("eO<h>"))
B.cg=new A.nY()
B.aF=new A.h8(A.aJ("h8<0&>"))
B.aG=function getTagFallback(o) {
  var s = Object.prototype.toString.call(o);
  return s.substring(8, s.length - 1);
}
B.ch=function() {
  var toStringFunction = Object.prototype.toString;
  function getTag(o) {
    var s = toStringFunction.call(o);
    return s.substring(8, s.length - 1);
  }
  function getUnknownTag(object, tag) {
    if (/^HTML[A-Z].*Element$/.test(tag)) {
      var name = toStringFunction.call(object);
      if (name == "[object Object]") return null;
      return "HTMLElement";
    }
  }
  function getUnknownTagGenericBrowser(object, tag) {
    if (object instanceof HTMLElement) return "HTMLElement";
    return getUnknownTag(object, tag);
  }
  function prototypeForTag(tag) {
    if (typeof window == "undefined") return null;
    if (typeof window[tag] == "undefined") return null;
    var constructor = window[tag];
    if (typeof constructor != "function") return null;
    return constructor.prototype;
  }
  function discriminator(tag) { return null; }
  var isBrowser = typeof HTMLElement == "function";
  return {
    getTag: getTag,
    getUnknownTag: isBrowser ? getUnknownTagGenericBrowser : getUnknownTag,
    prototypeForTag: prototypeForTag,
    discriminator: discriminator };
}
B.cm=function(getTagFallback) {
  return function(hooks) {
    if (typeof navigator != "object") return hooks;
    var userAgent = navigator.userAgent;
    if (typeof userAgent != "string") return hooks;
    if (userAgent.indexOf("DumpRenderTree") >= 0) return hooks;
    if (userAgent.indexOf("Chrome") >= 0) {
      function confirm(p) {
        return typeof window == "object" && window[p] && window[p].name == p;
      }
      if (confirm("Window") && confirm("HTMLElement")) return hooks;
    }
    hooks.getTag = getTagFallback;
  };
}
B.ci=function(hooks) {
  if (typeof dartExperimentalFixupGetTag != "function") return hooks;
  hooks.getTag = dartExperimentalFixupGetTag(hooks.getTag);
}
B.cl=function(hooks) {
  if (typeof navigator != "object") return hooks;
  var userAgent = navigator.userAgent;
  if (typeof userAgent != "string") return hooks;
  if (userAgent.indexOf("Firefox") == -1) return hooks;
  var getTag = hooks.getTag;
  var quickMap = {
    "BeforeUnloadEvent": "Event",
    "DataTransfer": "Clipboard",
    "GeoGeolocation": "Geolocation",
    "Location": "!Location",
    "WorkerMessageEvent": "MessageEvent",
    "XMLDocument": "!Document"};
  function getTagFirefox(o) {
    var tag = getTag(o);
    return quickMap[tag] || tag;
  }
  hooks.getTag = getTagFirefox;
}
B.ck=function(hooks) {
  if (typeof navigator != "object") return hooks;
  var userAgent = navigator.userAgent;
  if (typeof userAgent != "string") return hooks;
  if (userAgent.indexOf("Trident/") == -1) return hooks;
  var getTag = hooks.getTag;
  var quickMap = {
    "BeforeUnloadEvent": "Event",
    "DataTransfer": "Clipboard",
    "HTMLDDElement": "HTMLElement",
    "HTMLDTElement": "HTMLElement",
    "HTMLPhraseElement": "HTMLElement",
    "Position": "Geoposition"
  };
  function getTagIE(o) {
    var tag = getTag(o);
    var newTag = quickMap[tag];
    if (newTag) return newTag;
    if (tag == "Object") {
      if (window.DataView && (o instanceof window.DataView)) return "DataView";
    }
    return tag;
  }
  function prototypeForTagIE(tag) {
    var constructor = window[tag];
    if (constructor == null) return null;
    return constructor.prototype;
  }
  hooks.getTag = getTagIE;
  hooks.prototypeForTag = prototypeForTagIE;
}
B.cj=function(hooks) {
  var getTag = hooks.getTag;
  var prototypeForTag = hooks.prototypeForTag;
  function getTagFixed(o) {
    var tag = getTag(o);
    if (tag == "Document") {
      if (!!o.xmlVersion) return "!Document";
      return "!HTMLDocument";
    }
    return tag;
  }
  function prototypeForTagFixed(tag) {
    if (tag == "Document") return null;
    return prototypeForTag(tag);
  }
  hooks.getTag = getTagFixed;
  hooks.prototypeForTag = prototypeForTagFixed;
}
B.aH=function(hooks) { return hooks; }

B.k=new A.kb()
B.q=new A.kd()
B.cn=new A.kw()
B.co=new A.qK()
B.d=new A.rC()
B.ml=new A.rO(0,"midnight")
B.cp=new A.l6()
B.l=new A.lF()
B.cq=new A.to()
B.Y=new A.m0()
B.m=new A.mC()
B.M=new A.mJ()
B.ct=new A.jr(0,"elevated")
B.cu=new A.jr(1,"flat")
B.cv=new A.jt(null)
B.aI=new A.oh(0,"primary")
B.cw=new A.dd(null)
B.cx=new A.eA(null)
B.aX=s([],A.aJ("D<cy>"))
B.cy=new A.h0(B.aX)
B.ab=new A.bU(0,"connecting")
B.N=new A.bU(1,"live")
B.B=new A.bU(2,"degraded")
B.w=new A.bU(3,"offline")
B.cz=new A.oH(3,"stretch")
B.Z=new A.ca(0)
B.O=new A.ca(2e6)
B.mh=new A.ca(3e7)
B.cA=new A.oV(1,"md")
B.cB=new A.jJ(0,"centered")
B.cC=new A.jJ(2,"card")
B.cD=new A.jK(null)
B.cE=new A.eE(null)
B.cF=new A.jP(null)
B.cG=new A.eG(null)
B.aJ=new A.dg(0,"healthy")
B.aK=new A.dg(1,"warning")
B.aL=new A.dg(2,"critical")
B.cH=new A.dg(3,"offline")
B.Q=s([],t.cy)
B.cI=new A.dh(B.Q,0,"Not a valid fleet export file")
B.cJ=new A.dh(B.Q,0,"Invalid JSON")
B.cK=new A.dh(B.Q,0,"Missing or invalid servers list")
B.cL=new A.dh(B.Q,0,"Not a reactor-fleet export file")
B.cM=new A.pg(0,"top")
B.ac=new A.jU(0,"hover")
B.cN=new A.jU(1,"click")
B.cO=new A.jV("'Inter', ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif")
B.mi=new A.jV("'Inter', ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, sans-serif")
B.aM=new A.he(2,"sm")
B.cP=new A.he(6,"xl")
B.cQ=new A.he(7,"xl2")
B.aN=new A.pl(5,"w600")
B.n=s([],t.i)
B.ad=new A.bK(B.n,null)
B.aO=new A.hf(0,"success")
B.cR=new A.hf(1,"warning")
B.cS=new A.hf(2,"error")
B.cT=new A.eH(null)
B.cU=new A.eK(null)
B.aP=new A.cd(0,"xs")
B.a_=new A.cd(1,"sm")
B.f=new A.cd(2,"md")
B.aQ=new A.cd(3,"lg")
B.cV=new A.eL(null)
B.cW=new A.k1(null)
B.cX=new A.cF("email",6,"email")
B.cY=new A.cF("number",11,"number")
B.cZ=new A.cF("password",12,"password")
B.d_=new A.cF("search",16,"search")
B.d0=new A.cF("tel",18,"tel")
B.d1=new A.cF("text",0,"text")
B.d2=new A.cF("url",20,"url")
B.d3=new A.k5(null)
B.d5=new A.k7(null)
B.d9=new A.q1(null)
B.da=new A.q2(null)
B.db=new A.dR(0,"boolType")
B.dc=new A.dR(1,"intType")
B.dd=new A.dR(2,"doubleType")
B.aR=new A.dR(3,"stringType")
B.de=new A.dR(4,"enumType")
B.df=new A.q9(!1,255)
B.dg=new A.qa(255)
B.aS=new A.qb(2,"snug")
B.bO=new A.ah("ALL","ALL",!1)
B.bR=new A.ah("INFO","INFO",!1)
B.bS=new A.ah("WARN","WARN",!1)
B.bQ=new A.ah("ERROR","ERROR",!1)
B.bP=new A.ah("DEBUG","DEBUG",!1)
B.dh=s([B.bO,B.bR,B.bS,B.bQ,B.bP],t.lZ)
B.aT=s(["iris-biome-cache-hit-rate","iris-chunk-stream-ms","iris-pregen-queue"],t.s)
B.aU=s(["wormholes-block-changes","wormholes-packets","wormholes-portals","wormholes-projection-observers","wormholes-projection-render-ms","wormholes-projections-active","wormholes-spoofed-entities","wormholes-traversals"],t.s)
B.jV=new A.k('#arcane-root.shadcn-midnight {\n  --background: #02040a;\n  --foreground: #edf5ff;\n  --card: #07101c;\n  --card-foreground: #edf5ff;\n  --card-hover: #0b1830;\n  --popover: #07101c;\n  --popover-foreground: #edf5ff;\n  --primary: #1f66ff;\n  --primary-foreground: #f8fbff;\n  --secondary: #0a1428;\n  --secondary-foreground: #d8e7ff;\n  --muted: #08111f;\n  --muted-foreground: #8ba0c4;\n  --accent: #102a61;\n  --accent-foreground: #f8fbff;\n  --border: #163462;\n  --input: #1a3c72;\n  --ring: #4f8dff;\n  --info: #2f7dff;\n  --success: #27d17f;\n  --warning: #f3b34f;\n  --destructive: #ff4b6b;\n  --reactor-black: #02040a;\n  --reactor-panel: #07101c;\n  --reactor-panel-strong: #09172b;\n  --reactor-line: color-mix(in srgb, var(--primary) 22%, var(--border));\n  --reactor-label: #95a9cf;\n  --reactor-blue-glow: color-mix(in srgb, var(--primary) 32%, transparent);\n  --radius: 0.5rem;\n}\n\n#arcane-root.shadcn-midnight,\n#arcane-root.shadcn-midnight .arcane-scaffold,\n#arcane-root.shadcn-midnight .arcane-scaffold-main,\n#arcane-root.shadcn-midnight .arcane-scaffold-body {\n  background:\n    linear-gradient(135deg, rgba(31, 102, 255, 0.11), transparent 24rem),\n    linear-gradient(180deg, #02040a 0%, #030711 42%, #02040a 100%) !important;\n  min-height: 100vh;\n}\n\n#arcane-root.shadcn-midnight .arcane-scaffold-main {\n  position: relative;\n}\n\n#arcane-root.shadcn-midnight .arcane-scaffold-main::before {\n  content: "";\n  position: fixed;\n  inset: 0;\n  pointer-events: none;\n  background-image:\n    linear-gradient(rgba(79, 141, 255, 0.045) 1px, transparent 1px),\n    linear-gradient(90deg, rgba(79, 141, 255, 0.035) 1px, transparent 1px);\n  background-size: 56px 56px;\n  mask-image: linear-gradient(to bottom, rgba(0, 0, 0, 0.9), transparent 78%);\n}\n\n#arcane-root.shadcn-midnight .arcane-scaffold-sidebar {\n  border-right: 1px solid var(--reactor-line) !important;\n  background:\n    linear-gradient(180deg, rgba(31, 102, 255, 0.12), transparent 18rem),\n    linear-gradient(180deg, #050b16 0%, #03060d 100%) !important;\n  box-shadow: 14px 0 44px rgba(0, 0, 0, 0.34);\n  min-height: 100vh;\n}\n\n#arcane-root.shadcn-midnight.arcane-theme-shadcn .arcane-scaffold-sidebar.arcane-scaffold-sidebar {\n  position: sticky !important;\n  inset: 0 auto auto 0 !important;\n  top: 0 !important;\n  align-self: stretch !important;\n  width: 19rem !important;\n  height: 100vh !important;\n  min-height: 100vh !important;\n}\n\n#arcane-root.shadcn-midnight .arcane-sidebar,\n#arcane-root.shadcn-midnight .shadcn-sidebar {\n  border: 0 !important;\n  border-radius: 0 !important;\n  background:\n    linear-gradient(180deg, rgba(31, 102, 255, 0.1), transparent 18rem),\n    linear-gradient(180deg, rgba(7, 16, 28, 0.96), rgba(2, 4, 10, 0.88)) !important;\n  box-shadow:\n    inset -1px 0 0 var(--reactor-line),\n    inset 1px 0 0 rgba(255, 255, 255, 0.04);\n  overflow: hidden;\n  position: relative;\n}\n\n#arcane-root.shadcn-midnight .arcane-sidebar::before,\n#arcane-root.shadcn-midnight .shadcn-sidebar::before {\n  content: "";\n  position: absolute;\n  inset: 0;\n  pointer-events: none;\n  background-image:\n    linear-gradient(rgba(79, 141, 255, 0.045) 1px, transparent 1px),\n    linear-gradient(90deg, rgba(79, 141, 255, 0.035) 1px, transparent 1px);\n  background-size: 42px 42px;\n  opacity: 0.45;\n  mask-image: linear-gradient(to bottom, #000 0%, transparent 74%);\n}\n\n#arcane-root.shadcn-midnight.arcane-theme-shadcn .arcane-scaffold-sidebar .arcane-sidebar.arcane-sidebar {\n  height: 100vh !important;\n  min-height: 100vh !important;\n  display: flex !important;\n  flex-direction: column !important;\n}\n\n#arcane-root.shadcn-midnight.arcane-theme-shadcn .arcane-scaffold-sidebar .sidebar-nav {\n  flex: 1 1 auto !important;\n}\n\n#arcane-root.shadcn-midnight .sidebar-header {\n  position: relative;\n  z-index: 1;\n  padding: 1rem 0.95rem 1rem;\n  border-bottom: 1px solid color-mix(in srgb, var(--primary) 18%, transparent);\n  background:\n    linear-gradient(135deg, rgba(31, 102, 255, 0.16), transparent 58%),\n    rgba(2, 4, 10, 0.36);\n}\n\n#arcane-root.shadcn-midnight .sidebar-nav {\n  position: relative;\n  z-index: 1;\n  padding: 1rem 0.85rem !important;\n  scrollbar-width: thin;\n  scrollbar-color: color-mix(in srgb, var(--primary) 70%, transparent) transparent;\n}\n\n#arcane-root.shadcn-midnight .arcane-sidebar-footer {\n  position: relative;\n  z-index: 1;\n  padding: 0.95rem !important;\n  border-top: 1px solid color-mix(in srgb, var(--primary) 18%, transparent);\n  background:\n    linear-gradient(0deg, rgba(31, 102, 255, 0.11), transparent),\n    rgba(2, 4, 10, 0.4);\n}\n\n#arcane-root.shadcn-midnight .arcane-card {\n  border-color: color-mix(in srgb, var(--primary) 18%, var(--border)) !important;\n  background:\n    linear-gradient(180deg, rgba(31, 102, 255, 0.075), transparent 42%),\n    var(--card) !important;\n  box-shadow:\n    0 18px 48px rgba(0, 0, 0, 0.34),\n    inset 0 1px 0 rgba(255, 255, 255, 0.045) !important;\n}\n\n#arcane-root.shadcn-midnight .arcane-card:hover {\n  border-color: color-mix(in srgb, var(--primary) 38%, var(--border)) !important;\n}\n\n#arcane-root.shadcn-midnight .arcane-button {\n  border-radius: 0.375rem !important;\n  letter-spacing: 0 !important;\n}\n\n#arcane-root.shadcn-midnight .arcane-button:not([disabled]) {\n  box-shadow: 0 0 0 1px color-mix(in srgb, var(--primary) 12%, transparent);\n}\n\n#arcane-root.shadcn-midnight .arcane-button:hover:not([disabled]) {\n  box-shadow:\n    0 0 0 1px color-mix(in srgb, var(--primary) 32%, transparent),\n    0 10px 24px rgba(31, 102, 255, 0.18);\n}\n\n.reactor-shell-content {\n  position: relative;\n  z-index: 1;\n  min-height: 100vh;\n  display: flex;\n  flex-direction: column;\n}\n\n.reactor-page-header {\n  background:\n    linear-gradient(90deg, rgba(31, 102, 255, 0.12), transparent 56%),\n    linear-gradient(180deg, rgba(255, 255, 255, 0.025), transparent);\n}\n\n.reactor-panel,\n.reactor-metric-card {\n  background:\n    linear-gradient(180deg, rgba(255, 255, 255, 0.018), transparent),\n    var(--reactor-panel);\n}\n\n.reactor-brand {\n  display: grid;\n  grid-template-columns: 44px minmax(0, 1fr);\n  gap: 0.75rem;\n  align-items: center;\n}\n.reactor-brand-mark {\n  width: 44px;\n  height: 44px;\n  border-radius: 0.6rem;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  background:\n    linear-gradient(135deg, #2b74ff, #06101f 72%);\n  border: 1px solid color-mix(in srgb, var(--primary) 64%, transparent);\n  color: var(--foreground);\n  box-shadow:\n    0 0 0 6px rgba(31, 102, 255, 0.08),\n    0 0 34px var(--reactor-blue-glow);\n}\n.reactor-brand-body {\n  min-width: 0;\n  display: flex;\n  flex-direction: column;\n  gap: 0.2rem;\n}\n.reactor-brand-title-row {\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  gap: 0.6rem;\n}\n.reactor-brand-title {\n  color: var(--foreground);\n  font-size: 1.08rem;\n  font-weight: 800;\n  line-height: 1;\n}\n.reactor-brand-subtitle {\n  color: var(--reactor-label);\n  font-size: 0.7rem;\n  font-weight: 650;\n  line-height: 1;\n  text-transform: uppercase;\n}\n.reactor-brand-chip {\n  flex: 0 0 auto;\n  display: inline-flex;\n  align-items: center;\n  gap: 0.35rem;\n  padding: 0.26rem 0.45rem;\n  border-radius: 999px;\n  border: 1px solid color-mix(in srgb, var(--primary) 28%, transparent);\n  color: #c8dcff;\n  background: color-mix(in srgb, var(--primary) 14%, transparent);\n  font-size: 0.68rem;\n  font-weight: 750;\n  line-height: 1;\n}\n.reactor-brand-meta {\n  grid-column: 1 / -1;\n  display: grid;\n  grid-template-columns: repeat(2, minmax(0, 1fr));\n  gap: 0.55rem;\n  padding-top: 0.9rem;\n}\n.reactor-brand-stat {\n  min-width: 0;\n  padding: 0.65rem;\n  border-radius: 0.5rem;\n  border: 1px solid color-mix(in srgb, var(--primary) 14%, transparent);\n  background: rgba(2, 4, 10, 0.45);\n}\n.reactor-brand-stat-label {\n  display: block;\n  color: var(--muted-foreground);\n  font-size: 0.66rem;\n  font-weight: 700;\n  line-height: 1;\n  text-transform: uppercase;\n}\n.reactor-brand-stat-value {\n  display: block;\n  margin-top: 0.35rem;\n  color: var(--foreground);\n  font-size: 0.86rem;\n  font-weight: 800;\n  line-height: 1;\n  white-space: nowrap;\n  overflow: hidden;\n  text-overflow: ellipsis;\n}\n.reactor-nav { display: flex; flex-direction: column; gap: 0.22rem; }\n.reactor-nav-label {\n  display: flex; align-items: center; height: 28px; padding: 0 0.35rem;\n  font-size: 0.68rem; font-weight: 800; letter-spacing: 0;\n  text-transform: uppercase; color: var(--reactor-label);\n}\n.reactor-nav-item {\n  display: flex; align-items: center; gap: 0.7rem; width: 100%;\n  min-height: 42px;\n  padding: 0.42rem 0.48rem; border-radius: 0.5rem; background: transparent;\n  border: 1px solid transparent; color: var(--muted-foreground); font-size: 0.86rem;\n  font-weight: 500; line-height: 1.2; cursor: pointer; text-align: left;\n  font-family: inherit; position: relative;\n  transition: background-color 0.12s ease, color 0.12s ease, border-color 0.12s ease, box-shadow 0.12s ease;\n}\n.reactor-nav-item:hover {\n  background: color-mix(in srgb, var(--primary) 10%, transparent);\n  border-color: color-mix(in srgb, var(--primary) 24%, transparent);\n  color: var(--foreground);\n}\n.reactor-nav-ico {\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  flex: 0 0 auto;\n  width: 28px;\n  height: 28px;\n  border-radius: 0.42rem;\n  color: currentColor;\n  background: rgba(79, 141, 255, 0.07);\n  border: 1px solid color-mix(in srgb, var(--primary) 10%, transparent);\n}\n.reactor-nav-item.active {\n  background:\n    linear-gradient(90deg, color-mix(in srgb, var(--primary) 34%, transparent), color-mix(in srgb, var(--primary) 8%, transparent));\n  border-color: color-mix(in srgb, var(--primary) 48%, transparent);\n  color: var(--foreground); font-weight: 700;\n  box-shadow:\n    inset 3px 0 0 #78a8ff,\n    0 0 22px color-mix(in srgb, var(--primary) 15%, transparent);\n}\n.reactor-nav-item.active .reactor-nav-ico {\n  color: var(--foreground);\n  background: color-mix(in srgb, var(--primary) 28%, transparent);\n  border-color: color-mix(in srgb, var(--primary) 42%, transparent);\n  box-shadow: 0 0 18px rgba(31, 102, 255, 0.22);\n}\n.reactor-nav-section { display: flex; flex-direction: column; gap: 1px; }\n.reactor-nav-section-header {\n  display: flex; align-items: center; gap: 0.5rem; padding: 0.4rem 0.6rem;\n  margin-top: 0.4rem; font-size: 0.6875rem; font-weight: 600;\n  letter-spacing: 0; text-transform: uppercase;\n  color: var(--reactor-label);\n}\n.reactor-nav-section-name {\n  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; min-width: 0;\n}\n\n.reactor-server-list {\n  display: flex;\n  flex-direction: column;\n  gap: 0.45rem;\n  margin-top: 0.45rem;\n}\n\n.reactor-server-list-label {\n  display: flex;\n  align-items: center;\n  height: 28px;\n  padding: 0 0.35rem;\n  color: var(--reactor-label);\n  font-size: 0.68rem;\n  font-weight: 800;\n  line-height: 1;\n  text-transform: uppercase;\n}\n\n.reactor-server-list-scroll {\n  display: flex;\n  flex-direction: column;\n  gap: 0.28rem;\n  max-height: 15rem;\n  overflow-y: auto;\n  padding-right: 0.2rem;\n  scrollbar-width: thin;\n  scrollbar-color: color-mix(in srgb, var(--primary) 62%, transparent) transparent;\n}\n\n.reactor-server-row {\n  display: grid;\n  grid-template-columns: auto minmax(0, 1fr) auto;\n  align-items: center;\n  gap: 0.55rem;\n  width: 100%;\n  min-height: 38px;\n  padding: 0.42rem 0.55rem;\n  border-radius: 0.48rem;\n  border: 1px solid color-mix(in srgb, var(--primary) 10%, transparent);\n  color: var(--muted-foreground);\n  background: rgba(2, 4, 10, 0.28);\n  font-family: inherit;\n  cursor: pointer;\n  text-align: left;\n}\n\n.reactor-server-row:hover {\n  border-color: color-mix(in srgb, var(--primary) 28%, transparent);\n  background: color-mix(in srgb, var(--primary) 10%, transparent);\n  color: var(--foreground);\n}\n\n.reactor-server-row.active {\n  color: var(--foreground);\n  border-color: color-mix(in srgb, var(--primary) 46%, transparent);\n  background:\n    linear-gradient(90deg, color-mix(in srgb, var(--primary) 24%, transparent), rgba(2, 4, 10, 0.36));\n  box-shadow:\n    inset 3px 0 0 #78a8ff,\n    0 0 18px rgba(31, 102, 255, 0.14);\n}\n\n.reactor-server-row-name {\n  min-width: 0;\n  overflow: hidden;\n  text-overflow: ellipsis;\n  white-space: nowrap;\n  font-size: 0.82rem;\n  font-weight: 720;\n}\n\n.reactor-server-row-state {\n  color: var(--reactor-label);\n  font-size: 0.66rem;\n  font-weight: 800;\n  line-height: 1;\n  text-transform: uppercase;\n}\n\n.reactor-sidebar-status {\n  display: flex;\n  flex-direction: column;\n  gap: 0.75rem;\n  padding: 0.8rem;\n  border-radius: 0.6rem;\n  border: 1px solid color-mix(in srgb, var(--primary) 18%, transparent);\n  background:\n    linear-gradient(145deg, rgba(31, 102, 255, 0.14), transparent 62%),\n    rgba(2, 4, 10, 0.58);\n}\n.reactor-sidebar-status-top {\n  display: flex;\n  align-items: flex-start;\n  justify-content: space-between;\n  gap: 0.75rem;\n}\n.reactor-sidebar-status-copy {\n  min-width: 0;\n  display: flex;\n  flex-direction: column;\n  gap: 0.25rem;\n}\n.reactor-sidebar-status-title {\n  color: var(--foreground);\n  font-size: 0.82rem;\n  font-weight: 800;\n  line-height: 1.15;\n}\n.reactor-sidebar-status-subtitle {\n  color: var(--muted-foreground);\n  font-size: 0.7rem;\n  line-height: 1.2;\n}\n.reactor-sidebar-action {\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  gap: 0.45rem;\n  min-height: 34px;\n  width: 100%;\n  border-radius: 0.45rem;\n  border: 1px solid color-mix(in srgb, var(--primary) 48%, transparent);\n  color: var(--foreground);\n  background:\n    linear-gradient(180deg, #2f7dff, #1554df);\n  font-family: inherit;\n  font-size: 0.8rem;\n  font-weight: 750;\n  cursor: pointer;\n  box-shadow: 0 12px 24px rgba(31, 102, 255, 0.18);\n}\n.reactor-sidebar-action:hover {\n  background:\n    linear-gradient(180deg, #4b8dff, #1f66ff);\n}\n\n.reactor-first-run {\n  min-height: calc(100vh - 3.5rem);\n  display: grid;\n  grid-template-columns: minmax(0, 1.35fr) minmax(340px, 0.65fr);\n  gap: clamp(1rem, 2.2vw, 1.6rem);\n  align-items: stretch;\n  width: 100%;\n}\n\n.reactor-first-run-hero,\n.reactor-first-run-side {\n  border: 1px solid color-mix(in srgb, var(--primary) 22%, var(--border));\n  border-radius: 0.625rem;\n  background:\n    linear-gradient(145deg, rgba(31, 102, 255, 0.16), transparent 42%),\n    linear-gradient(180deg, rgba(255, 255, 255, 0.035), transparent),\n    rgba(5, 11, 22, 0.92);\n  box-shadow:\n    0 28px 80px rgba(0, 0, 0, 0.42),\n    inset 0 1px 0 rgba(255, 255, 255, 0.055);\n}\n\n.reactor-first-run-hero {\n  position: relative;\n  overflow: hidden;\n  padding: clamp(1.25rem, 4vw, 3.75rem);\n  display: flex;\n  flex-direction: column;\n  justify-content: space-between;\n  min-height: 620px;\n}\n\n.reactor-first-run-hero::before {\n  content: "";\n  position: absolute;\n  inset: 0;\n  pointer-events: none;\n  background:\n    radial-gradient(circle at 18% 16%, rgba(31, 102, 255, 0.34), transparent 24rem),\n    linear-gradient(90deg, rgba(79, 141, 255, 0.08) 1px, transparent 1px),\n    linear-gradient(rgba(79, 141, 255, 0.06) 1px, transparent 1px);\n  background-size: auto, 44px 44px, 44px 44px;\n  opacity: 0.8;\n}\n\n.reactor-first-run-main,\n.reactor-first-run-proof {\n  position: relative;\n  z-index: 1;\n}\n\n.reactor-empty-mark {\n  width: 54px;\n  height: 54px;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  border-radius: 0.625rem;\n  color: var(--foreground);\n  background:\n    linear-gradient(135deg, var(--primary), #061226 78%);\n  border: 1px solid color-mix(in srgb, var(--primary) 58%, transparent);\n  box-shadow:\n    0 0 0 8px rgba(31, 102, 255, 0.08),\n    0 0 44px rgba(31, 102, 255, 0.34);\n}\n\n.reactor-empty-kicker {\n  margin-top: 1.3rem;\n  color: var(--reactor-label);\n  font-size: 0.75rem;\n  font-weight: 800;\n  text-transform: uppercase;\n  letter-spacing: 0;\n}\n\n.reactor-empty-title {\n  max-width: 780px;\n  margin-top: 0.85rem;\n  color: var(--foreground);\n  font-size: clamp(2.25rem, 5vw, 5.6rem);\n  font-weight: 800;\n  line-height: 0.94;\n  letter-spacing: 0;\n}\n\n.reactor-empty-copy {\n  max-width: 660px;\n  margin-top: 1.1rem;\n  color: #aec2e8;\n  font-size: clamp(0.98rem, 1.4vw, 1.18rem);\n  line-height: 1.55;\n}\n\n.reactor-empty-actions {\n  display: flex;\n  flex-wrap: wrap;\n  gap: 0.7rem;\n  margin-top: 1.45rem;\n}\n\n.reactor-first-run-proof {\n  display: grid;\n  grid-template-columns: repeat(3, minmax(0, 1fr));\n  gap: 0.75rem;\n  margin-top: 2rem;\n}\n\n.reactor-proof-cell {\n  padding: 0.95rem;\n  border-radius: 0.5rem;\n  border: 1px solid color-mix(in srgb, var(--primary) 18%, transparent);\n  background: rgba(2, 4, 10, 0.48);\n}\n\n.reactor-proof-value {\n  display: block;\n  color: var(--foreground);\n  font-size: 1.45rem;\n  font-weight: 800;\n  line-height: 1;\n  font-variant-numeric: tabular-nums;\n}\n\n.reactor-proof-label {\n  display: block;\n  margin-top: 0.4rem;\n  color: var(--muted-foreground);\n  font-size: 0.78rem;\n  line-height: 1.25;\n}\n\n.reactor-first-run-side {\n  padding: clamp(1rem, 2vw, 1.35rem);\n  display: flex;\n  flex-direction: column;\n  gap: 0.95rem;\n  min-height: 620px;\n}\n\n.reactor-side-panel {\n  border: 1px solid color-mix(in srgb, var(--primary) 16%, transparent);\n  border-radius: 0.5rem;\n  background: rgba(2, 4, 10, 0.5);\n  overflow: hidden;\n}\n\n.reactor-side-panel-head {\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  gap: 0.75rem;\n  padding: 0.85rem 0.95rem;\n  border-bottom: 1px solid color-mix(in srgb, var(--primary) 14%, transparent);\n  background: linear-gradient(90deg, rgba(31, 102, 255, 0.13), transparent);\n}\n\n.reactor-side-title {\n  color: var(--foreground);\n  font-size: 0.85rem;\n  font-weight: 800;\n}\n\n.reactor-side-body {\n  padding: 0.9rem 0.95rem;\n  display: flex;\n  flex-direction: column;\n  gap: 0.7rem;\n}\n\n.reactor-signal-row {\n  display: grid;\n  grid-template-columns: 94px 1fr auto;\n  gap: 0.65rem;\n  align-items: center;\n  color: var(--muted-foreground);\n  font-size: 0.78rem;\n}\n\n.reactor-signal-bar {\n  height: 7px;\n  border-radius: 999px;\n  background: rgba(79, 141, 255, 0.12);\n  overflow: hidden;\n}\n\n.reactor-signal-fill {\n  display: block;\n  width: var(--signal);\n  height: 100%;\n  border-radius: inherit;\n  background: linear-gradient(90deg, #1f66ff, #78a8ff);\n  box-shadow: 0 0 18px rgba(31, 102, 255, 0.34);\n}\n\n.reactor-command-line {\n  display: flex;\n  align-items: center;\n  gap: 0.6rem;\n  min-height: 38px;\n  color: #c9d9f6;\n  font-size: 0.78rem;\n  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;\n}\n\n.reactor-command-dot {\n  width: 8px;\n  height: 8px;\n  border-radius: 999px;\n  background: var(--primary);\n  box-shadow: 0 0 18px rgba(31, 102, 255, 0.72);\n  flex: 0 0 auto;\n}\n\n.reactor-step-list {\n  display: grid;\n  gap: 0.65rem;\n}\n\n.reactor-step {\n  display: grid;\n  grid-template-columns: 30px 1fr;\n  gap: 0.65rem;\n  align-items: center;\n  padding: 0.75rem;\n  border-radius: 0.5rem;\n  border: 1px solid color-mix(in srgb, var(--primary) 14%, transparent);\n  background: rgba(7, 16, 28, 0.66);\n}\n\n.reactor-step-index {\n  width: 30px;\n  height: 30px;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  border-radius: 0.4rem;\n  color: var(--foreground);\n  font-size: 0.78rem;\n  font-weight: 800;\n  background: color-mix(in srgb, var(--primary) 24%, transparent);\n  border: 1px solid color-mix(in srgb, var(--primary) 32%, transparent);\n}\n\n.reactor-step-title {\n  color: var(--foreground);\n  font-size: 0.84rem;\n  font-weight: 750;\n  line-height: 1.2;\n}\n\n.reactor-step-copy {\n  margin-top: 0.25rem;\n  color: var(--muted-foreground);\n  font-size: 0.74rem;\n  line-height: 1.3;\n}\n\n.reactor-add-layout {\n  display: grid;\n  grid-template-columns: minmax(0, 1.25fr) minmax(320px, 0.75fr);\n  gap: 1rem;\n  align-items: start;\n}\n\n.reactor-add-actions {\n  display: flex;\n  align-items: center;\n  justify-content: flex-end;\n  gap: 0.5rem;\n  flex-wrap: wrap;\n}\n\n.reactor-add-console {\n  border: 1px solid color-mix(in srgb, var(--primary) 22%, transparent);\n  border-radius: 0.5rem;\n  overflow: hidden;\n  background:\n    linear-gradient(135deg, rgba(31, 102, 255, 0.12), transparent 48%),\n    rgba(2, 4, 10, 0.56);\n}\n\n.reactor-add-console-head {\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  gap: 0.75rem;\n  padding: 0.85rem 0.95rem;\n  border-bottom: 1px solid color-mix(in srgb, var(--primary) 16%, transparent);\n  background: rgba(4, 10, 20, 0.72);\n}\n\n.reactor-add-console-title {\n  display: flex;\n  align-items: center;\n  gap: 0.55rem;\n  color: var(--foreground);\n  font-size: 0.86rem;\n  font-weight: 800;\n}\n\n.reactor-add-console-body {\n  display: flex;\n  flex-direction: column;\n  gap: 0.9rem;\n  padding: 0.95rem;\n}\n\n.reactor-add-message {\n  border-radius: 0.45rem;\n  border: 1px solid color-mix(in srgb, var(--info) 28%, transparent);\n  background: color-mix(in srgb, var(--info) 11%, transparent);\n  color: #d8e7ff;\n  font-size: 0.82rem;\n  line-height: 1.4;\n  padding: 0.7rem 0.8rem;\n}\n\n.reactor-add-message.warning {\n  border-color: color-mix(in srgb, var(--warning) 42%, transparent);\n  background: color-mix(in srgb, var(--warning) 13%, transparent);\n  color: #ffe9bd;\n}\n\n.reactor-add-detail-grid {\n  display: grid;\n  grid-template-columns: repeat(2, minmax(0, 1fr));\n  gap: 0.65rem;\n}\n\n.reactor-add-detail,\n.reactor-add-step {\n  border: 1px solid color-mix(in srgb, var(--primary) 14%, transparent);\n  border-radius: 0.45rem;\n  background: rgba(7, 16, 28, 0.58);\n  padding: 0.78rem;\n}\n\n.reactor-add-detail-label,\n.reactor-add-step-label {\n  display: block;\n  color: var(--reactor-label);\n  font-size: 0.68rem;\n  font-weight: 800;\n  line-height: 1;\n  text-transform: uppercase;\n}\n\n.reactor-add-detail-value {\n  display: block;\n  margin-top: 0.42rem;\n  color: var(--foreground);\n  font-size: 0.92rem;\n  font-weight: 760;\n  line-height: 1.2;\n  overflow: hidden;\n  text-overflow: ellipsis;\n  white-space: nowrap;\n}\n\n.reactor-add-side {\n  display: flex;\n  flex-direction: column;\n  gap: 1rem;\n}\n\n.reactor-add-step-list {\n  display: grid;\n  gap: 0.65rem;\n}\n\n.reactor-add-step-copy {\n  margin-top: 0.35rem;\n  color: var(--muted-foreground);\n  font-size: 0.78rem;\n  line-height: 1.35;\n}\n\n@media (max-width: 900px) {\n  #arcane-root.shadcn-midnight .arcane-scaffold-body {\n    display: flex !important;\n    flex-direction: column !important;\n  }\n\n  #arcane-root.shadcn-midnight .arcane-scaffold-sidebar {\n    width: 100% !important;\n    min-height: auto !important;\n    height: auto !important;\n    border-right: none !important;\n    border-bottom: 1px solid var(--reactor-line) !important;\n    position: relative !important;\n    top: 0 !important;\n    inset: auto !important;\n    align-self: auto !important;\n    padding: 0.75rem !important;\n    box-sizing: border-box;\n  }\n\n  #arcane-root.shadcn-midnight.arcane-theme-shadcn .arcane-scaffold-sidebar.arcane-scaffold-sidebar {\n    position: relative !important;\n    inset: auto !important;\n    top: 0 !important;\n    align-self: auto !important;\n    width: 100% !important;\n    height: auto !important;\n    min-height: 0 !important;\n  }\n\n  #arcane-root.shadcn-midnight .arcane-sidebar,\n  #arcane-root.shadcn-midnight .shadcn-sidebar {\n    width: 100% !important;\n    min-width: 0 !important;\n    border-right: none !important;\n    max-height: 18rem !important;\n    height: auto !important;\n    min-height: 0 !important;\n    overflow: hidden !important;\n  }\n\n  #arcane-root.shadcn-midnight.arcane-theme-shadcn .arcane-scaffold-sidebar .arcane-sidebar.arcane-sidebar {\n    height: auto !important;\n    min-height: 0 !important;\n    max-height: 18rem !important;\n  }\n\n  #arcane-root.shadcn-midnight .sidebar-header {\n    padding: 0.2rem 0.35rem 0.55rem !important;\n    border-bottom: 0;\n  }\n\n  #arcane-root.shadcn-midnight .sidebar-nav {\n    max-height: 9.75rem !important;\n    overflow-x: auto !important;\n    overflow-y: hidden !important;\n    padding: 0.45rem !important;\n    scrollbar-width: thin;\n    display: grid !important;\n    gap: 0.45rem !important;\n  }\n\n  #arcane-root.shadcn-midnight .arcane-sidebar-footer {\n    padding: 0.45rem 0.7rem !important;\n    border-top: 1px solid color-mix(in srgb, var(--primary) 16%, transparent);\n  }\n\n  .reactor-brand {\n    display: flex;\n    align-items: center;\n  }\n\n  .reactor-brand-meta,\n  .reactor-brand-chip {\n    display: none;\n  }\n\n  .reactor-nav {\n    flex-direction: row;\n    align-items: center;\n    gap: 0.4rem;\n    width: max-content;\n    min-width: 100%;\n  }\n\n  .reactor-nav-section {\n    flex-direction: row;\n    align-items: center;\n    gap: 0.4rem;\n    width: max-content;\n    min-width: 100%;\n  }\n\n  .reactor-nav-section-header {\n    flex: 0 0 auto;\n    margin-top: 0;\n    min-height: 36px;\n    padding: 0.5rem 0.6rem;\n    border-radius: 0.5rem;\n    border: 1px solid color-mix(in srgb, var(--success) 35%, transparent);\n    background: rgba(39, 209, 127, 0.08);\n  }\n\n  .reactor-server-list {\n    width: max-content;\n    min-width: 100%;\n    margin-top: 0;\n  }\n\n  .reactor-server-list-label {\n    display: none;\n  }\n\n  .reactor-server-list-scroll {\n    flex-direction: row;\n    max-height: none;\n    overflow-x: auto;\n    overflow-y: hidden;\n    padding-right: 0;\n    padding-bottom: 0.15rem;\n  }\n\n  .reactor-server-row {\n    flex: 0 0 auto;\n    width: auto;\n    min-width: 9.5rem;\n  }\n\n  .reactor-nav-label {\n    display: none;\n  }\n\n  .reactor-nav-item {\n    flex: 0 0 auto;\n    width: auto;\n    white-space: nowrap;\n    padding: 0.55rem 0.7rem;\n    min-height: 36px;\n  }\n\n  .reactor-nav-ico {\n    width: 22px;\n    height: 22px;\n  }\n\n  .reactor-sidebar-status {\n    flex-direction: row;\n    align-items: center;\n    justify-content: space-between;\n    padding: 0.48rem 0.6rem;\n    border-radius: 0.5rem;\n  }\n\n  .reactor-sidebar-status-top {\n    width: 100%;\n    align-items: center;\n  }\n\n  .reactor-sidebar-status-subtitle {\n    display: none;\n  }\n\n  .reactor-sidebar-action {\n    display: none;\n  }\n\n  .reactor-shell-content {\n    padding: 1rem !important;\n    min-height: auto;\n  }\n\n  .reactor-page-header {\n    align-items: stretch !important;\n    padding-left: 0.75rem !important;\n  }\n\n  .reactor-grid {\n    grid-template-columns: 1fr !important;\n  }\n\n  .reactor-add-layout {\n    grid-template-columns: 1fr;\n  }\n\n  .reactor-add-actions {\n    justify-content: stretch;\n    width: 100%;\n  }\n\n  .reactor-add-actions .arcane-button {\n    flex: 1 1 auto;\n  }\n\n  .reactor-first-run {\n    min-height: auto;\n    grid-template-columns: 1fr;\n  }\n\n  .reactor-first-run-hero,\n  .reactor-first-run-side {\n    min-height: auto;\n  }\n\n  .reactor-first-run-proof {\n    grid-template-columns: 1fr;\n  }\n}\n\n@media (max-width: 560px) {\n  .reactor-nav-item {\n    padding: 0.55rem 0.5rem;\n    font-size: 0.8rem;\n  }\n\n  .reactor-nav-item span:last-child {\n    max-width: 8.5rem;\n    overflow: hidden;\n    text-overflow: ellipsis;\n  }\n\n  .reactor-page {\n    gap: 16px !important;\n  }\n\n  .reactor-page-header {\n    gap: 0.75rem !important;\n  }\n\n  .reactor-first-run-hero,\n  .reactor-first-run-side {\n    padding: 1rem;\n  }\n\n  .reactor-add-detail-grid {\n    grid-template-columns: 1fr;\n  }\n\n  .reactor-empty-title {\n    font-size: 2.2rem;\n    line-height: 1;\n  }\n\n  .reactor-empty-actions {\n    flex-direction: column;\n  }\n\n  .reactor-empty-actions .arcane-button {\n    width: 100%;\n  }\n\n  .reactor-signal-row {\n    grid-template-columns: 78px 1fr;\n  }\n\n  .reactor-signal-row span:last-child {\n    grid-column: 2;\n  }\n}\n',null)
B.dk=s([B.jV],t.i)
B.aV=s(["adapt-ability-checks-per-tick","adapt-ability-ops","adapt-session-load","adapt-world-policy-latency"],t.s)
B.j9=new A.A("Off","off")
B.j7=new A.A("Light","light")
B.j0=new A.A("Balanced","balanced")
B.j6=new A.A("High","high")
B.dl=s([B.j9,B.j7,B.j0,B.j6],t.m_)
B.dm=s(["https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap"],t.s)
B.dn=s(["ticks-per-second","tick-time","players","entities","chunks","memory-used","incident-score","gc-time-percent","player-ping-p95"],t.s)
B.dp=s(["cpu","memory","jvm","server"],t.s)
B.dv=s([],A.aJ("D<da>"))
B.dw=s([],A.aJ("D<cr>"))
B.R=s([],t.eB)
B.dq=s([],t.a2)
B.aW=s([],t.k6)
B.ds=s([],t.n5)
B.du=s([],A.aJ("D<cE>"))
B.aY=s([],A.aJ("D<aR>"))
B.dr=s([],t.kV)
B.mj=s([],A.aJ("D<c0>"))
B.P=s([],t.s)
B.dx=s([],A.aJ("D<c2>"))
B.j=s([],t.gk)
B.dt=s([],t.dG)
B.dy=s([B.X,B.r,B.E],A.aJ("D<cs>"))
B.iU=new A.A("entity-pressure-heatmap","Entity Pressure")
B.jj=new A.A("chunk-load-gen-cost-map","Chunk Load/Gen Cost")
B.j_=new A.A("chunk-sampler-map","Chunk Sampler")
B.j3=new A.A("redstone-activity-heatmap","Redstone Activity")
B.jm=new A.A("hopper-container-throughput-map","Hopper Throughput")
B.j1=new A.A("tick-spike-origin-replay-map","Tick-Spike Origin")
B.iZ=new A.A("plugin-event-impact-pie-map","Event Impact (pie)")
B.jk=new A.A("plugin-event-impact-list-map","Event Impact (list)")
B.iY=new A.A("iris-biome-chunk-share-pie-map","Iris Biome Share")
B.jl=new A.A("iris-world-chunk-share-pie-map","Iris World Share")
B.dz=s([B.iU,B.jj,B.j_,B.j3,B.jm,B.j1,B.iZ,B.jk,B.iY,B.jl],t.m_)
B.aZ=s(["B","KB","MB","GB","TB","PB"],t.s)
B.dA=new A.eT(null)
B.dB=new A.qn(0,"start")
B.mk=new A.qo(1,"max")
B.i1={top:0,left:1,transform:2,"margin-top":3}
B.dQ=new A.i(B.i1,["100%","50%","translateX(-50%) translateY(4px)","8px"],t.w)
B.i0={bottom:0,left:1,transform:2,"margin-bottom":3}
B.dT=new A.i(B.i0,["100%","50%","translateX(-50%) translateY(-4px)","8px"],t.w)
B.iq={role:0}
B.eh=new A.i(B.iq,["tooltip"],t.w)
B.hA={right:0,top:1,transform:2,"margin-right":3}
B.ek=new A.i(B.hA,["100%","50%","translateY(-50%) translateX(-4px)","8px"],t.w)
B.hZ={role:0,"aria-modal":1,"data-arcane-autofocus":2}
B.er=new A.i(B.hZ,["dialog","true",""],t.w)
B.iw={top:0,transform:1}
B.b_=new A.i(B.iw,["50%","translateY(-50%)"],t.w)
B.hY={left:0,top:1,transform:2,"margin-left":3}
B.ew=new A.i(B.hY,["100%","50%","translateY(-50%) translateX(4px)","8px"],t.w)
B.ie={left:0}
B.b0=new A.i(B.ie,["0"],t.w)
B.io={bottom:0,left:1,"margin-bottom":2}
B.eS=new A.i(B.io,["100%","0","8px"],t.w)
B.hO={top:0,left:1,"margin-top":2}
B.eY=new A.i(B.hO,["100%","0","8px"],t.w)
B.ij={"iso_8859-1:1987":0,"iso-ir-100":1,"iso_8859-1":2,"iso-8859-1":3,latin1:4,l1:5,ibm819:6,cp819:7,csisolatin1:8,"iso-ir-6":9,"ansi_x3.4-1968":10,"ansi_x3.4-1986":11,"iso_646.irv:1991":12,"iso646-us":13,"us-ascii":14,us:15,ibm367:16,cp367:17,csascii:18,ascii:19,csutf8:20,"utf-8":21}
B.p=new A.je()
B.f2=new A.i(B.ij,[B.q,B.q,B.q,B.q,B.q,B.q,B.q,B.q,B.q,B.p,B.p,B.p,B.p,B.p,B.p,B.p,B.p,B.p,B.p,B.p,B.l,B.l],A.aJ("i<b,df>"))
B.ix={type:0}
B.ae=new A.i(B.ix,["button"],t.w)
B.ai={}
B.b1=new A.i(B.ai,[],A.aJ("i<b,q<b>>"))
B.x=new A.i(B.ai,[],t.w)
B.a0=new A.i(B.ai,[],A.aJ("i<b,u?>"))
B.hJ={top:0,right:1,"margin-top":2}
B.fi=new A.i(B.hJ,["100%","0","8px"],t.w)
B.ip={right:0}
B.b2=new A.i(B.ip,["0"],t.w)
B.ig={left:0,transform:1}
B.b3=new A.i(B.ig,["50%","translateX(-50%)"],t.w)
B.is={svg:0,math:1}
B.fE=new A.i(B.is,["http://www.w3.org/2000/svg","http://www.w3.org/1998/Math/MathML"],t.w)
B.h7={bottom:0,right:1,"margin-bottom":2}
B.fO=new A.i(B.h7,["100%","0","8px"],t.w)
B.il={preserveAspectRatio:0}
B.fR=new A.i(B.il,["none"],t.w)
B.h0=new A.kh(null)
B.h1=new A.ki(null)
B.iG=new A.eZ(null)
B.iH=new A.kx(null)
B.iI=new A.kB(null)
B.bj=new A.hG(0,"normal")
B.iJ=new A.hG(1,"pressure")
B.iK=new A.hG(2,"panic")
B.bk=new A.dm("server identity mismatch (possible relay MITM)")
B.aj=new A.dm("Authentication failed (401)")
B.iL=new A.b8("Malformed heatmaps response")
B.iM=new A.b8("Malformed response: missing data object")
B.iN=new A.b8("No path available")
B.iO=new A.b8("Malformed worlds response")
B.iP=new A.b8("Malformed actions response")
B.iQ=new A.b8("Malformed response: missing data list")
B.iR=new A.b8("Malformed logs response")
B.C=new A.dV(0,"healthy")
B.t=new A.dV(1,"warning")
B.K=new A.dV(2,"critical")
B.D=new A.dV(3,"info")
B.bl=new A.dV(4,"neutral")
B.iS=new A.A("var(--destructive)","var(--destructive)")
B.iT=new A.A(20,50)
B.S=new A.A(40,70)
B.ak=new A.A(5,10)
B.iV=new A.A(5,15)
B.iW=new A.A("var(--warning, #f59e0b)","var(--warning, #f59e0b)")
B.iX=new A.A(60,85)
B.bm=new A.A("Critical",B.K)
B.bn=new A.A("Degraded",B.t)
B.j2=new A.A("var(--primary)","var(--muted)")
B.j4=new A.A("var(--info, #3b82f6)","var(--muted)")
B.j5=new A.A("Healthy",B.C)
B.j8=new A.A("var(--secondary)","var(--muted)")
B.bo=new A.A("Offline",B.K)
B.ja=new A.A("var(--primary)","var(--primary)")
B.bp=new A.A("Warning",B.t)
B.jb=new A.A("auto","auto")
B.jc=new A.A("auto","hidden")
B.jd=new A.A("var(--warning, #f59e0b)","var(--muted)")
B.je=new A.A("var(--destructive)","var(--muted)")
B.jf=new A.A("var(--secondary)","var(--secondary)")
B.jg=new A.A("hidden","auto")
B.jh=new A.A("var(--success, #22c55e)","var(--success, #22c55e)")
B.ji=new A.A("var(--info, #3b82f6)","var(--info, #3b82f6)")
B.jn=new A.A(10,16.7)
B.jo=new A.A("var(--success, #22c55e)","var(--muted)")
B.jp=new A.b2([36,20,16,2])
B.jq=new A.b2([44,24,20,2])
B.jr=new A.b2([56,28,24,2])
B.js=new A.b2(["40px","0.75rem","0.5rem","0.875rem"])
B.jt=new A.b2(["48px","1rem","0.75rem","1rem"])
B.ju=new A.b2(["var(--warning, #f59e0b)","var(--warning-foreground, #000000)",null,null])
B.bq=new A.b2(["var(--secondary)","var(--secondary-foreground)",null,null])
B.jv=new A.b2(["var(--destructive)","var(--destructive-foreground)",null,null])
B.jw=new A.b2(["var(--success, #22c55e)","var(--success-foreground, #ffffff)",null,null])
B.br=new A.b2(["var(--primary)","var(--primary-foreground)","0 0 15px color-mix(in srgb, var(--primary) 20%, transparent)",null])
B.jx=new A.b2(["transparent","var(--foreground)",null,"1px solid var(--border)"])
B.jy=new A.b2(["var(--info, #3b82f6)","var(--info-foreground, #ffffff)",null,null])
B.jz=new A.b2(["32px","0.5rem","0.25rem","0.75rem"])
B.jA=new A.ej(["64px","1.25rem","1.125rem","2rem","1rem"])
B.jB=new A.ej(["32px","1rem","0.875rem","1rem","0.5rem"])
B.jC=new A.ej(["48px","1.125rem","1rem","1.5rem","0.75rem"])
B.al=new A.dW(0,"none")
B.bs=new A.dW(1,"direct")
B.jD=new A.dW(2,"relay")
B.bt=new A.hN(0,"idle")
B.jE=new A.hN(1,"midFrameCallback")
B.jF=new A.hN(2,"postFrameCallbacks")
B.jG=new A.rz(0,"vertical")
B.jH=new A.rA(1,"thin")
B.jI=new A.rB(1,"hover")
B.ic={"dynamic-view-distance":0,"dynamic-activation-range":1,"activation-range-governor":2,"tracker-range-governor":3,"random-tick-governor":4,"pathfinder-budget":5,"per-world-tick-budget":6,"afk-view-shedding":7,"adaptive-entity-sleep":8,"incident-mode":9,"feature-trinity-incident-mode":10,"circuit-manager":11,"feature-adapt-runtime-surge-guard":12,"feature-iris-terrain-surge-guard":13}
B.jJ=new A.h5(B.ic,14,A.aJ("h5<b>"))
B.jK=new A.dp(null)
B.jL=new A.hP(1,"right")
B.mm=new A.rQ(2,"end")
B.jM=new A.hP(2,"top")
B.jN=new A.hP(3,"bottom")
B.jO=new A.hQ(0,"auto")
B.jP=new A.hQ(2,"md")
B.jQ=new A.hQ(5,"full")
B.mn=new A.rS(2,"md")
B.jR=new A.cl(0,"online")
B.jS=new A.cl(2,"busy")
B.jT=new A.cl(3,"away")
B.bv=new A.ls(0,"primary")
B.bw=new A.ls(2,"muted")
B.V=new A.lw(0,"text")
B.W=new A.lw(3,"number")
B.jW=new A.ff(1,"success")
B.jX=new A.ff(2,"warning")
B.an=new A.ff(3,"error")
B.jY=new A.ff(4,"loading")
B.jZ=new A.fg(null)
B.k_=A.bu("yv")
B.k0=A.bu("yw")
B.k1=A.bu("pe")
B.k2=A.bu("pf")
B.k3=A.bu("pX")
B.k4=A.bu("pY")
B.k5=A.bu("pZ")
B.k6=A.bu("a4")
B.k7=A.bu("u")
B.k8=A.bu("b")
B.k9=A.bu("th")
B.ka=A.bu("ti")
B.kb=A.bu("tj")
B.kc=A.bu("hX")
B.bx=A.bu("BS")
B.kd=new A.tn(!1)
B.ke=new A.fi(null)
B.kf=new A.lK(null)
B.u=new A.fm(0,"initial")
B.L=new A.fm(1,"active")
B.ki=new A.fm(2,"inactive")
B.kj=new A.fm(3,"defunct")
B.mo=new A.m7("em",2)
B.kk=new A.ma(null)
B.ag={"font-size":0,"font-weight":1,color:2,"line-height":3,"letter-spacing":4,"font-variant-numeric":5}
B.eW=new A.i(B.ag,["1.7rem","700","var(--foreground)","1","0","tabular-nums"],t.w)
B.km=new A.l(null,null,null,null,B.eW)
B.hM={height:0,"background-color":1,"border-radius":2,overflow:3}
B.dH=new A.i(B.hM,["4px","var(--border)","2px","hidden"],t.w)
B.kn=new A.l(null,null,null,null,B.dH)
B.J={display:0,"flex-direction":1,gap:2}
B.eC=new A.i(B.J,["flex","column","0.45rem"],t.w)
B.ko=new A.l(null,null,null,null,B.eC)
B.hw={display:0,"flex-direction":1,gap:2,padding:3,overflow:4,"border-radius":5}
B.h_=new A.i(B.hw,["flex","column","0.6rem","0.95rem 1rem","hidden","0.5rem"],t.w)
B.kp=new A.l(null,null,null,null,B.h_)
B.ha={display:0,"justify-content":1,padding:2}
B.ed=new A.i(B.ha,["flex","center","1rem 0"],t.w)
B.ap=new A.l(null,null,null,null,B.ed)
B.bh={width:0}
B.eU=new A.i(B.bh,["100px"],t.w)
B.kq=new A.l(null,null,null,null,B.eU)
B.ho={display:0,"align-items":1,"justify-content":2,gap:3,padding:4,"padding-top":5,"flex-shrink":6}
B.fU=new A.i(B.ho,["flex","flex-start","space-between","16px","24px","12px","0"],t.w)
B.kr=new A.l(null,null,null,null,B.fU)
B.h5={"font-size":0,color:1,padding:2}
B.fH=new A.i(B.h5,["0.85rem","var(--muted-foreground)","0.5rem 0"],t.w)
B.ks=new A.l(null,null,null,null,B.fH)
B.be={padding:0,display:1,"flex-direction":2,gap:3}
B.f1=new A.i(B.be,["0.75rem","flex","column","0.5rem"],t.w)
B.a3=new A.l(null,null,null,null,B.f1)
B.h6={position:0,inset:1,"z-index":2,"pointer-events":3}
B.fg=new A.i(B.h6,["fixed","0","1100","auto"],t.w)
B.kt=new A.l(null,null,null,null,B.fg)
B.hp={flex:0,"font-size":1,color:2}
B.eB=new A.i(B.hp,["1","0.875rem","var(--muted-foreground)"],t.w)
B.ku=new A.l(null,null,null,null,B.eB)
B.he={"font-family":0,"font-size":1,"white-space":2}
B.dP=new A.i(B.he,["monospace","0.8rem","pre-wrap"],t.w)
B.kv=new A.l(null,null,null,null,B.dP)
B.eG=new A.i(B.J,["flex","column","0.25rem"],t.w)
B.by=new A.l(null,null,null,null,B.eG)
B.ib={position:0,width:1,height:2}
B.fr=new A.i(B.ib,["relative","132px","132px"],t.w)
B.kw=new A.l(null,null,null,null,B.fr)
B.a1={display:0,"flex-direction":1,gap:2,"min-width":3}
B.fn=new A.i(B.a1,["flex","column","0.25rem","0"],t.w)
B.kx=new A.l(null,null,null,null,B.fn)
B.hB={display:0,"align-items":1,"justify-content":2,gap:3,padding:4,cursor:5,"list-style":6,"-webkit-user-select":7,"user-select":8}
B.fN=new A.i(B.hB,["flex","center","space-between","1rem","1rem 1.25rem","pointer","none","none","none"],t.w)
B.ky=new A.l(null,null,null,null,B.fN)
B.hl={"min-height":0,display:1,"flex-direction":2,background:3,color:4}
B.fl=new A.i(B.hl,["100vh","flex","column","var(--background)","var(--foreground)"],t.w)
B.kz=new A.l(null,null,null,null,B.fl)
B.bd={display:0,"align-items":1,"justify-content":2,gap:3,padding:4}
B.fS=new A.i(B.bd,["flex","flex-end","space-between","1rem","1rem 1.15rem 0.85rem"],t.w)
B.kA=new A.l(null,null,null,null,B.fS)
B.bc={display:0,"flex-direction":1,"align-items":2,gap:3}
B.eK=new A.i(B.bc,["flex","column","center","0.55rem"],t.w)
B.kB=new A.l(null,null,null,null,B.eK)
B.fp=new A.i(B.a1,["flex","column","0.4rem","0"],t.w)
B.kC=new A.l(null,null,null,null,B.fp)
B.ba={"font-weight":0,"font-size":1}
B.dN=new A.i(B.ba,["600","0.9rem"],t.w)
B.kD=new A.l(null,null,null,null,B.dN)
B.b6={display:0,"align-items":1,gap:2,"flex-wrap":3}
B.e_=new A.i(B.b6,["flex","center","0.5rem","wrap"],t.w)
B.aq=new A.l(null,null,null,null,B.e_)
B.iD={display:0,"flex-direction":1,"align-items":2,gap:3,padding:4}
B.fz=new A.i(B.iD,["flex","column","center","0.5rem","1rem"],t.w)
B.kE=new A.l(null,null,null,null,B.fz)
B.b8={"font-size":0,color:1,"line-height":2}
B.fB=new A.i(B.b8,["0.875rem","var(--muted-foreground)","1.4"],t.w)
B.kF=new A.l(null,null,null,null,B.fB)
B.i4={flex:0}
B.dC=new A.i(B.i4,["1"],t.w)
B.bz=new A.l(null,null,null,null,B.dC)
B.ah={"font-size":0,"font-weight":1,color:2}
B.fL=new A.i(B.ah,["0.8rem","600","var(--foreground)"],t.w)
B.kG=new A.l(null,null,null,null,B.fL)
B.eH=new A.i(B.J,["flex","column","0.5rem"],t.w)
B.bA=new A.l(null,null,null,null,B.eH)
B.I={"font-size":0,color:1}
B.eb=new A.i(B.I,["0.85rem","var(--muted-foreground)"],t.w)
B.bB=new A.l(null,null,null,null,B.eb)
B.ir={padding:0,"border-top":1}
B.f8=new A.i(B.ir,["0 1.25rem 1rem 1.25rem","1px solid rgba(255, 255, 255, 0.06)"],t.w)
B.kH=new A.l(null,null,null,null,B.f8)
B.im={display:0,gap:1,"align-items":2}
B.fd=new A.i(B.im,["flex","0.5rem","center"],t.w)
B.kI=new A.l(null,null,null,null,B.fd)
B.h8={"font-size":0,"font-weight":1,color:2,"letter-spacing":3}
B.dR=new A.i(B.h8,["0.75rem","500","var(--muted-foreground)","0"],t.w)
B.kJ=new A.l(null,null,null,null,B.dR)
B.hn={"font-size":0,"font-weight":1,"letter-spacing":2,"text-transform":3,color:4,"line-height":5}
B.eN=new A.i(B.hn,["0.6875rem","600","0","uppercase","var(--reactor-label)","1"],t.w)
B.z=new A.l(null,null,null,null,B.eN)
B.hz={display:0,"align-items":1,gap:2,flex:3}
B.dJ=new A.i(B.hz,["flex","center","0.5rem","0 0 auto"],t.w)
B.kK=new A.l(null,null,null,null,B.dJ)
B.fm=new A.i(B.a1,["flex","column","0.3rem","0"],t.w)
B.bC=new A.l(null,null,null,null,B.fm)
B.b7={display:0,"align-items":1,gap:2,"min-width":3}
B.dL=new A.i(B.b7,["flex","center","0.85rem","0"],t.w)
B.kL=new A.l(null,null,null,null,B.dL)
B.b4={color:0,"font-size":1}
B.ey=new A.i(B.b4,["var(--muted-foreground)","0.85rem"],t.w)
B.kM=new A.l(null,null,null,null,B.ey)
B.ht={display:0,"flex-wrap":1,"align-items":2,gap:3}
B.eI=new A.i(B.ht,["flex","wrap","center","0.5rem"],t.w)
B.kN=new A.l(null,null,null,null,B.eI)
B.dM=new A.i(B.b7,["flex","center","0.55rem","0"],t.w)
B.bD=new A.l(null,null,null,null,B.dM)
B.bf={padding:0}
B.fb=new A.i(B.bf,["0.25rem 1.15rem 1.1rem"],t.w)
B.kO=new A.l(null,null,null,null,B.fb)
B.hX={color:0}
B.dW=new A.i(B.hX,["var(--muted-foreground)"],t.w)
B.bE=new A.l(null,null,null,null,B.dW)
B.eX=new A.i(B.ag,["1.6rem","700","var(--foreground)","1","0","tabular-nums"],t.w)
B.kP=new A.l(null,null,null,null,B.eX)
B.iE={"font-size":0,"font-weight":1,color:2,"font-variant-numeric":3,"text-align":4}
B.ej=new A.i(B.iE,["0.85rem","500","var(--foreground)","tabular-nums","right"],t.w)
B.kQ=new A.l(null,null,null,null,B.ej)
B.e7=new A.i(B.I,["var(--font-size-sm)","var(--destructive)"],t.w)
B.kR=new A.l(null,null,null,null,B.e7)
B.hc={"flex-grow":0,"font-size":1,"font-weight":2,color:3,"line-height":4}
B.fk=new A.i(B.hc,["1","var(--font-size-sm)","var(--font-weight-medium)","var(--foreground)","1.5"],t.w)
B.kS=new A.l(null,null,null,null,B.fk)
B.fT=new A.i(B.bd,["flex","center","space-between","0.5rem","0.85rem 1rem 0.7rem"],t.w)
B.kT=new A.l(null,null,null,null,B.fT)
B.hb={flex:0,"min-height":1,display:2,"flex-direction":3,gap:4,"overflow-y":5,"overflow-x":6,padding:7}
B.ft=new A.i(B.hb,["1","0","flex","column","0.5rem","auto","hidden","0.75rem"],t.w)
B.kU=new A.l(null,null,null,null,B.ft)
B.hj={flex:0,"font-weight":1}
B.eu=new A.i(B.hj,["1","500"],t.w)
B.kV=new A.l(null,null,null,null,B.eu)
B.hK={"min-width":0,padding:1,background:2,overflow:3}
B.fY=new A.i(B.hK,["0","0","var(--background)","visible"],t.w)
B.kW=new A.l(null,null,null,null,B.fY)
B.hv={display:0,"flex-direction":1,overflow:2,"border-radius":3}
B.ee=new A.i(B.hv,["flex","column","hidden","0.5rem"],t.w)
B.ar=new A.l(null,null,null,null,B.ee)
B.hq={color:0,"font-size":1,padding:2}
B.f6=new A.i(B.hq,["var(--muted-foreground)","0.875rem","0.5rem 0"],t.w)
B.kX=new A.l(null,null,null,null,B.f6)
B.i2={flex:0,overflow:1,padding:2}
B.dK=new A.i(B.i2,["1","auto","0 24px 24px"],t.w)
B.kY=new A.l(null,null,null,null,B.dK)
B.hr={position:0,inset:1,"background-color":2,animation:3}
B.eL=new A.i(B.hr,["absolute","0","rgba(0, 0, 0, 0.8)","arcane-fade-in var(--transition-slow)"],t.w)
B.kZ=new A.l(null,null,null,null,B.eL)
B.eV=new A.i(B.ag,["1.75rem","700","var(--foreground)","1","0","tabular-nums"],t.w)
B.l_=new A.l(null,null,null,null,B.eV)
B.hT={display:0,gap:1,"flex-wrap":2}
B.fy=new A.i(B.hT,["flex","0.5rem","wrap"],t.w)
B.l0=new A.l(null,null,null,null,B.fy)
B.bb={display:0,"justify-content":1,"align-items":2,gap:3,padding:4,"border-bottom":5}
B.fG=new A.i(B.bb,["flex","space-between","center","1rem","0.6rem 1.15rem",u.h],t.w)
B.l1=new A.l(null,null,null,null,B.fG)
B.ii={padding:0,"text-align":1,color:2,"font-size":3}
B.eZ=new A.i(B.ii,["2rem","center","var(--muted-foreground)","0.875rem"],t.w)
B.bF=new A.l(null,null,null,null,B.eZ)
B.b5={display:0,"flex-wrap":1,gap:2,"justify-content":3,"align-items":4,padding:5}
B.f4=new A.i(B.b5,["flex","wrap","2rem","space-around","center","0.5rem 0"],t.w)
B.l2=new A.l(null,null,null,null,B.f4)
B.ia={"font-size":0,"font-weight":1,color:2,"line-height":3}
B.dZ=new A.i(B.ia,["0.9rem","600","var(--foreground)","1.25"],t.w)
B.l3=new A.l(null,null,null,null,B.dZ)
B.hy={display:0,"align-items":1,gap:2,padding:3,"border-bottom":4}
B.fV=new A.i(B.hy,["flex","center","1rem","0.65rem 1.15rem","1px solid var(--border)"],t.w)
B.l4=new A.l(null,null,null,null,B.fV)
B.f3=new A.i(B.b5,["flex","wrap","1.5rem","space-around","flex-start","0.5rem 0"],t.w)
B.l5=new A.l(null,null,null,null,B.f3)
B.eD=new A.i(B.J,["flex","column","2px"],t.w)
B.l6=new A.l(null,null,null,null,B.eD)
B.iu={"background-color":0,border:1,"border-radius":2}
B.eM=new A.i(B.iu,["var(--card)","1px solid var(--border)","var(--radius-md)"],t.w)
B.l7=new A.l(null,null,null,null,B.eM)
B.fa=new A.i(B.bf,["0 1rem 0.9rem"],t.w)
B.l8=new A.l(null,null,null,null,B.fa)
B.iv={display:0,"align-items":1,"justify-content":2,gap:3,"flex-wrap":4,padding:5,"border-bottom":6,"border-left":7}
B.e2=new A.i(B.iv,["flex","flex-start","space-between","1rem","wrap","0.15rem 0 1.15rem 0.9rem",u.h,"2px solid var(--primary)"],t.w)
B.l9=new A.l(null,null,null,null,B.e2)
B.bg={position:0,display:1}
B.e4=new A.i(B.bg,["relative","inline-block"],t.w)
B.la=new A.l(null,null,null,null,B.e4)
B.hQ={position:0,inset:1,"z-index":2,display:3,"align-items":4,"justify-content":5,padding:6,"background-color":7,animation:8}
B.fs=new A.i(B.hQ,["fixed","0","50","flex","center","center","24px","rgba(0, 0, 0, 0.8)","arcane-fade-in var(--transition-slow)"],t.w)
B.lb=new A.l(null,null,null,null,B.fs)
B.ik={display:0,gap:1,height:2,"align-items":3}
B.fh=new A.i(B.ik,["flex","3px","56px","flex-end"],t.w)
B.lc=new A.l(null,null,null,null,B.fh)
B.iF={display:0,"flex-direction":1,"justify-content":2,gap:3,padding:4,"flex-shrink":5}
B.fu=new A.i(B.iF,["flex","row","flex-end","var(--space-2)","0 24px 24px 24px","0"],t.w)
B.ld=new A.l(null,null,null,null,B.fu)
B.i3={"font-size":0,"font-weight":1}
B.fQ=new A.i(B.i3,["0.875rem","500"],t.w)
B.le=new A.l(null,null,null,null,B.fQ)
B.H={display:0,"align-items":1,gap:2}
B.ep=new A.i(B.H,["flex","center","0.25rem"],t.w)
B.lf=new A.l(null,null,null,null,B.ep)
B.hI={display:0,"justify-content":1}
B.ff=new A.i(B.hI,["flex","center"],t.w)
B.lg=new A.l(null,null,null,null,B.ff)
B.ea=new A.i(B.I,["var(--font-size-sm)","var(--muted-foreground)"],t.w)
B.lh=new A.l(null,null,null,null,B.ea)
B.i6={flex:0,"min-width":1,display:2,"flex-direction":3,gap:4}
B.dS=new A.i(B.i6,["1","0","flex","column","2px"],t.w)
B.li=new A.l(null,null,null,null,B.dS)
B.hm={display:0,gap:1,"font-size":2,"align-items":3}
B.eP=new A.i(B.hm,["flex","0.5rem","0.875rem","center"],t.w)
B.lj=new A.l(null,null,null,null,B.eP)
B.hf={"font-size":0,"font-weight":1,color:2,"text-transform":3,"letter-spacing":4}
B.ez=new A.i(B.hf,["0.6875rem","600","var(--muted-foreground)","uppercase","0"],t.w)
B.as=new A.l(null,null,null,null,B.ez)
B.eq=new A.i(B.H,["flex","baseline","0.3rem"],t.w)
B.bG=new A.l(null,null,null,null,B.eq)
B.eF=new A.i(B.J,["flex","column","0.75rem"],t.w)
B.a4=new A.l(null,null,null,null,B.eF)
B.f0=new A.i(B.be,["0.75rem","flex","column","0.75rem"],t.w)
B.lk=new A.l(null,null,null,null,B.f0)
B.hN={display:0,"flex-wrap":1,gap:2}
B.fI=new A.i(B.hN,["flex","wrap","0.75rem"],t.w)
B.ll=new A.l(null,null,null,null,B.fI)
B.ih={"min-height":0,"background-color":1,color:2,"font-family":3,"-webkit-font-smoothing":4,"-moz-osx-font-smoothing":5}
B.fC=new A.i(B.ih,["100vh","var(--background)","var(--foreground)","var(--font-sans)","antialiased","grayscale"],t.w)
B.lm=new A.l(null,null,null,null,B.fC)
B.id={padding:0,"max-width":1,width:2,margin:3,"box-sizing":4}
B.e3=new A.i(B.id,["1.75rem clamp(1rem, 3vw, 2.25rem)","1480px","100%","0 auto","border-box"],t.w)
B.ln=new A.l(null,null,null,null,B.e3)
B.hs={"padding-top":0,"font-size":1,color:2,"line-height":3}
B.eQ=new A.i(B.hs,["1rem","var(--font-size-sm)","var(--muted-foreground)","1.625"],t.w)
B.lo=new A.l(null,null,null,null,B.eQ)
B.h9={padding:0,"border-top":1,"flex-shrink":2}
B.ef=new A.i(B.h9,["8px","1px solid var(--border)","0"],t.w)
B.lp=new A.l(null,null,null,null,B.ef)
B.hi={padding:0,"border-radius":1,border:2,background:3,color:4,"font-size":5}
B.dE=new A.i(B.hi,["0.6rem 0.75rem","0.375rem","1px solid color-mix(in srgb, var(--destructive) 40%, transparent)","color-mix(in srgb, var(--destructive) 12%, transparent)","var(--destructive)","0.8rem"],t.w)
B.lq=new A.l(null,null,null,null,B.dE)
B.h3={width:0,height:1,display:2,"align-items":3,"justify-content":4,padding:5,border:6,background:7,color:8,cursor:9,"border-radius":10,"font-size":11,transition:12,"flex-shrink":13}
B.eR=new A.i(B.h3,["32px","32px","flex","center","center","0","none","transparent","var(--muted-foreground)","pointer","var(--radius-sm)","var(--font-size-xl)","color var(--transition), background var(--transition)","0"],t.w)
B.lr=new A.l(null,null,null,null,B.eR)
B.eo=new A.i(B.H,["flex","center","0.75rem"],t.w)
B.bH=new A.l(null,null,null,null,B.eo)
B.fF=new A.i(B.bb,["flex","space-between","center","0.75rem","0.65rem 0",u.h],t.w)
B.ls=new A.l(null,null,null,null,B.fF)
B.hU={display:0,"justify-content":1,"border-top":2,"padding-top":3}
B.eA=new A.i(B.hU,["flex","flex-end",u.h,"0.6rem"],t.w)
B.lt=new A.l(null,null,null,null,B.eA)
B.e0=new A.i(B.b6,["flex","center","0.75rem","wrap"],t.w)
B.lu=new A.l(null,null,null,null,B.e0)
B.e9=new A.i(B.I,["0.75rem","var(--muted-foreground)"],t.w)
B.at=new A.l(null,null,null,null,B.e9)
B.hG={display:0,"flex-direction":1}
B.eg=new A.i(B.hG,["flex","column"],t.w)
B.a5=new A.l(null,null,null,null,B.eg)
B.hk={"font-size":0,"font-weight":1,"line-height":2,color:3}
B.ev=new A.i(B.hk,["var(--font-size-sm)","var(--font-weight-medium)","1","var(--foreground)"],t.w)
B.lv=new A.l(null,null,null,null,B.ev)
B.hS={width:0,height:1,display:2}
B.f_=new A.i(B.hS,["100%","100%","block"],t.w)
B.lw=new A.l(null,null,null,null,B.f_)
B.hW={"font-size":0,"font-weight":1,"line-height":2,"letter-spacing":3,color:4}
B.dI=new A.i(B.hW,["var(--font-size-lg)","var(--font-weight-semibold)","1","-0.025em","var(--foreground)"],t.w)
B.lx=new A.l(null,null,null,null,B.dI)
B.i_={display:0}
B.es=new A.i(B.i_,["inline-block"],t.w)
B.ly=new A.l(null,null,null,null,B.es)
B.ex=new A.i(B.b4,["var(--muted-foreground)","0.875rem"],t.w)
B.i=new A.l(null,null,null,null,B.ex)
B.e5=new A.i(B.bg,["relative","inline-flex"],t.w)
B.lz=new A.l(null,null,null,null,B.e5)
B.hL={"font-size":0,color:1,"line-height":2,display:3,"-webkit-line-clamp":4,"-webkit-box-orient":5,overflow:6}
B.fc=new A.i(B.hL,["0.8rem","var(--muted-foreground)","1.4","-webkit-box","2","vertical","hidden"],t.w)
B.lA=new A.l(null,null,null,null,B.fc)
B.em=new A.i(B.H,["flex","center","0.5rem"],t.w)
B.a6=new A.l(null,null,null,null,B.em)
B.iB={"font-weight":0,"font-size":1,color:2,overflow:3,"text-overflow":4,"white-space":5}
B.fj=new A.i(B.iB,["600","0.95rem","var(--foreground)","hidden","ellipsis","nowrap"],t.w)
B.lB=new A.l(null,null,null,null,B.fj)
B.i5={display:0,"justify-content":1,"font-size":2,color:3}
B.fX=new A.i(B.i5,["flex","space-between","0.75rem","var(--muted-foreground)"],t.w)
B.lC=new A.l(null,null,null,null,B.fX)
B.fA=new A.i(B.b8,["0.8rem","var(--muted-foreground)","1.4"],t.w)
B.lD=new A.l(null,null,null,null,B.fA)
B.hC={padding:0,"overflow-y":1,flex:2}
B.fe=new A.i(B.hC,["24px","auto","1"],t.w)
B.lE=new A.l(null,null,null,null,B.fe)
B.en=new A.i(B.H,["flex","baseline","0.25rem"],t.w)
B.lF=new A.l(null,null,null,null,B.en)
B.h4={width:0,"border-right":1,background:2,padding:3,position:4,top:5,"align-self":6,height:7,"max-height":8,"min-height":9,overflow:10}
B.fv=new A.i(B.h4,["17.5rem","1px solid color-mix(in srgb, var(--border) 54%, transparent)","color-mix(in srgb, var(--background) 97%, var(--muted))","1rem","sticky","3.5rem","start","max-content","none","0","visible"],t.w)
B.lG=new A.l(null,null,null,null,B.fv)
B.i7={"font-size":0,color:1,"line-height":2,"margin-top":3}
B.fw=new A.i(B.i7,["var(--font-size-xs)","var(--muted-foreground)","1.5","4px"],t.w)
B.lH=new A.l(null,null,null,null,B.fw)
B.hg={"font-size":0,"font-weight":1,"letter-spacing":2,"line-height":3,color:4}
B.fx=new A.i(B.hg,["1.6rem","700","0","1.1","var(--foreground)"],t.w)
B.lI=new A.l(null,null,null,null,B.fx)
B.hE={"font-weight":0,"font-size":1,color:2}
B.fD=new A.i(B.hE,["500","0.9rem","var(--foreground)"],t.w)
B.lJ=new A.l(null,null,null,null,B.fD)
B.fo=new A.i(B.a1,["flex","column","0.2rem","0"],t.w)
B.lK=new A.l(null,null,null,null,B.fo)
B.hH={display:0,"grid-template-columns":1,gap:2}
B.fW=new A.i(B.hH,["grid","repeat(auto-fill, minmax(200px, 1fr))","1rem"],t.w)
B.bI=new A.l(null,null,null,null,B.fW)
B.e6=new A.i(B.I,["0.8rem","var(--muted-foreground)"],t.w)
B.bJ=new A.l(null,null,null,null,B.e6)
B.fM=new A.i(B.ah,["1rem","500","var(--foreground)"],t.w)
B.lL=new A.l(null,null,null,null,B.fM)
B.hu={"min-width":0,"font-size":1,color:2,"font-weight":3}
B.ei=new A.i(B.hu,["2rem","0.75rem","var(--muted-foreground)","600"],t.w)
B.lM=new A.l(null,null,null,null,B.ei)
B.iC={position:0,inset:1,display:2,"flex-direction":3,"align-items":4,"justify-content":5,gap:6}
B.et=new A.i(B.iC,["absolute","0","flex","column","center","center","0.15rem"],t.w)
B.lP=new A.l(null,null,null,null,B.et)
B.cr=new A.lQ()
B.kh=new A.i7("yellow")
B.kl=new A.my("rem",1)
B.kg=new A.i7("red")
B.lQ=new A.l(B.cr,B.kh,B.kl,B.kg,null)
B.hP={opacity:0,"pointer-events":1}
B.fP=new A.i(B.hP,["0.5","none"],t.w)
B.lR=new A.l(null,null,null,null,B.fP)
B.dO=new A.i(B.ba,["500","0.875rem"],t.w)
B.lS=new A.l(null,null,null,null,B.dO)
B.iA={display:0,gap:1,"padding-top":2}
B.ec=new A.i(B.iA,["flex","0.5rem","0.25rem"],t.w)
B.lT=new A.l(null,null,null,null,B.ec)
B.el=new A.i(B.H,["flex","center","0.6rem"],t.w)
B.bK=new A.l(null,null,null,null,B.el)
B.hV={background:0}
B.dU=new A.i(B.hV,["var(--muted)"],t.w)
B.lU=new A.l(null,null,null,null,B.dU)
B.hh={position:0,right:1,top:2,display:3,"align-items":4,"justify-content":5,width:6,height:7,"border-radius":8,background:9,border:10,opacity:11,color:12,cursor:13,transition:14,"font-size":15,padding:16}
B.fZ=new A.i(B.hh,["absolute","16px","16px","inline-flex","center","center","24px","24px","var(--radius-xs)","transparent","none","0.7","var(--foreground)","pointer","opacity var(--transition)","var(--font-size-base)","0"],t.w)
B.lV=new A.l(null,null,null,null,B.fZ)
B.b9={display:0,"align-items":1,"justify-content":2,gap:3}
B.dG=new A.i(B.b9,["flex","flex-start","space-between","0.75rem"],t.w)
B.lW=new A.l(null,null,null,null,B.dG)
B.eJ=new A.i(B.bc,["flex","column","center","1rem"],t.w)
B.lX=new A.l(null,null,null,null,B.eJ)
B.i9={display:0,"align-items":1,"justify-content":2,width:3,height:4,"flex-shrink":5,"margin-top":6}
B.dY=new A.i(B.i9,["flex","center","center","20px","20px","0","2px"],t.w)
B.lY=new A.l(null,null,null,null,B.dY)
B.iy={display:0,"flex-direction":1,gap:2,padding:3}
B.f9=new A.i(B.iy,["flex","column","0.4rem","0.75rem 1rem"],t.w)
B.lZ=new A.l(null,null,null,null,B.f9)
B.hF={flex:0,"font-size":1}
B.dV=new A.i(B.hF,["1","0.875rem"],t.w)
B.m_=new A.l(null,null,null,null,B.dV)
B.fK=new A.i(B.ah,["var(--font-size-lg)","var(--font-weight-semibold)","var(--foreground)"],t.w)
B.m0=new A.l(null,null,null,null,B.fK)
B.i8={flex:0,display:1,"flex-direction":2,gap:3}
B.fq=new A.i(B.i8,["1","flex","column","var(--space-1)"],t.w)
B.m1=new A.l(null,null,null,null,B.fq)
B.eE=new A.i(B.J,["flex","column","1.25rem"],t.w)
B.m2=new A.l(null,null,null,null,B.eE)
B.hd={"font-size":0,"font-weight":1,color:2,"line-height":3,"font-variant-numeric":4}
B.f7=new A.i(B.hd,["1.4rem","700","var(--foreground)","1","tabular-nums"],t.w)
B.m3=new A.l(null,null,null,null,B.f7)
B.e8=new A.i(B.I,["0.875rem","var(--foreground)"],t.w)
B.m4=new A.l(null,null,null,null,B.e8)
B.h2={display:0,"grid-template-columns":1,gap:2,padding:3,"border-top":4}
B.eO=new A.i(B.h2,["grid","repeat(2, 1fr)","0.75rem 1rem","0.5rem 1rem 0.9rem",u.h],t.w)
B.m5=new A.l(null,null,null,null,B.eO)
B.hR={display:0,"align-items":1,gap:2,color:3,"font-size":4}
B.e1=new A.i(B.hR,["flex","center","0.5rem","var(--muted-foreground)","0.875rem"],t.w)
B.m6=new A.l(null,null,null,null,B.e1)
B.eT=new A.i(B.bh,["140px"],t.w)
B.m7=new A.l(null,null,null,null,B.eT)
B.it={display:0,"align-items":1,"justify-content":2,padding:3,"padding-bottom":4,"flex-shrink":5}
B.dX=new A.i(B.it,["flex","flex-start","space-between","24px","0","0"],t.w)
B.m8=new A.l(null,null,null,null,B.dX)
B.hD={display:0,"justify-content":1,"font-size":2}
B.dD=new A.i(B.hD,["flex","space-between","0.8125rem"],t.w)
B.m9=new A.l(null,null,null,null,B.dD)
B.dF=new A.i(B.b9,["flex","center","space-between","0.5rem"],t.w)
B.ma=new A.l(null,null,null,null,B.dF)
B.hx={display:0,"align-items":1,"justify-content":2,width:3,height:4,color:5,"flex-shrink":6,transition:7}
B.fJ=new A.i(B.hx,["flex","center","center","16px","16px","var(--muted-foreground)","0","transform 0.2s ease"],t.w)
B.lO=new A.l(null,null,null,null,B.fJ)
B.iz={"font-size":0,"line-height":1}
B.f5=new A.i(B.iz,["0.625rem","1"],t.w)
B.lN=new A.l(null,null,null,null,B.f5)
B.jU=new A.k("\u25bc",null)
B.dj=s([B.jU],t.i)
B.mf=new A.ep(null,null,B.lN,null,B.dj,null)
B.di=s([B.mf],t.i)
B.mb=new A.c(null,"faq-chevron",B.lO,null,null,B.di,null)
B.mc=new A.c(null,null,null,null,null,B.n,null)
B.md=new A.nc(null)
B.me=new A.iX("https://cdn.jsdelivr.net/npm/uplot@1.6.31/dist/uPlot.iife.min.js",null,null)})();(function staticFields(){$.vn=null
$.bG=A.a([],t.hf)
$.AM=null
$.Ae=null
$.Ad=null
$.CA=null
$.Cn=null
$.CK=null
$.xW=null
$.y5=null
$.zl=null
$.vS=A.a([],A.aJ("D<q<u>?>"))
$.fy=null
$.iR=null
$.iS=null
$.zc=!1
$.a0=B.m
$.Bc=""
$.Bd=null
$.bD=null
$.zF=0
$.A8=0
$.An=!1
$.Ao=!1
$.B2=0
$.B4=0
$.B3=0
$.Aa=A.t(A.aJ("ji"),A.aJ("jh"))
$.aQ=1
$.BY=null
$.xg=null})();(function lazyInitializers(){var s=hunkHelpers.lazyFinal,r=hunkHelpers.lazy
s($,"Ir","yk",()=>A.Hq("_$dart_dartClosure"))
s($,"Ja","Di",()=>B.m.i8(new A.y9(),t.p8))
s($,"J6","Dg",()=>A.a([new J.k8()],A.aJ("D<hM>")))
s($,"IE","CV",()=>A.cZ(A.tf({
toString:function(){return"$receiver$"}})))
s($,"IF","CW",()=>A.cZ(A.tf({$method$:null,
toString:function(){return"$receiver$"}})))
s($,"IG","CX",()=>A.cZ(A.tf(null)))
s($,"IH","CY",()=>A.cZ(function(){var $argumentsExpr$="$arguments$"
try{null.$method$($argumentsExpr$)}catch(q){return q.message}}()))
s($,"IK","D0",()=>A.cZ(A.tf(void 0)))
s($,"IL","D1",()=>A.cZ(function(){var $argumentsExpr$="$arguments$"
try{(void 0).$method$($argumentsExpr$)}catch(q){return q.message}}()))
s($,"IJ","D_",()=>A.cZ(A.Ba(null)))
s($,"II","CZ",()=>A.cZ(function(){try{null.$method$}catch(q){return q.message}}()))
s($,"IN","D3",()=>A.cZ(A.Ba(void 0)))
s($,"IM","D2",()=>A.cZ(function(){try{(void 0).$method$}catch(q){return q.message}}()))
s($,"IO","zv",()=>A.Fa())
s($,"Is","iY",()=>t.cU.a($.Di()))
s($,"IU","D8",()=>A.AD(4096))
s($,"IS","D6",()=>new A.wX().$0())
s($,"IT","D7",()=>new A.wW().$0())
s($,"IQ","zw",()=>A.Es(A.C_(A.a([-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-2,-1,-2,-2,-2,-2,-2,62,-2,62,-2,63,52,53,54,55,56,57,58,59,60,61,-2,-2,-2,-1,-2,-2,-2,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,-2,-2,-2,-2,63,-2,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,-2,-2,-2,-2,-2],t.lC))))
r($,"IP","D4",()=>A.AD(0))
s($,"IR","D5",()=>A.ar("^[\\-\\.0-9A-Z_a-z~]*$",!0))
s($,"J_","cp",()=>A.eo(B.k7))
s($,"Io","CQ",()=>new A.jQ(new WeakMap(),"ArcaneStylesheetCss",A.aJ("jQ<b>")))
s($,"Ip","CR",()=>A.ar("^[\\w!#%&'*+\\-.^`|~]+$",!0))
s($,"IZ","Db",()=>A.ar('["\\x00-\\x1F\\x7F]',!0))
s($,"Jb","Dj",()=>A.ar('[^()<>@,;:"\\\\/[\\]?={} \\t\\x00-\\x1F\\x7F]+',!0))
s($,"J2","Dd",()=>A.ar("(?:\\r\\n)?[ \\t]+",!0))
s($,"J5","Df",()=>A.ar('"(?:[^"\\x00-\\x1F\\x7F\\\\]|\\\\.)*"',!0))
s($,"J4","De",()=>A.ar("\\\\(.)",!0))
s($,"J9","Dh",()=>A.ar('[()<>@,;:"\\\\/\\[\\]?={} \\t\\x00-\\x1F\\x7F]',!0))
s($,"Jc","Dk",()=>A.ar("(?:"+$.Dd().a+")*",!0))
s($,"Iq","CS",()=>new A.og().$0())
s($,"IV","yl",()=>A.y0(A.yj(),"Element",t.k))
s($,"IW","ym",()=>A.y0(A.yj(),"HTMLInputElement",t.k))
s($,"IX","D9",()=>A.y0(A.yj(),"HTMLSelectElement",t.k))
s($,"IY","Da",()=>A.y0(A.yj(),"Text",t.k))
r($,"Iy","zt",()=>A.EM(A.a([],t.E),A.bN(""),B.x))
s($,"J3","zy",()=>A.ar(":(\\w+)(\\((?:\\\\.|[^\\\\()])+\\))?",!0))
r($,"Iw","np",()=>new A.qJ(new A.jZ(),new A.kQ()))
s($,"J7","zz",()=>new A.ov($.zu()))
s($,"IB","CU",()=>new A.kD(A.ar("/",!0),A.ar("[^/]$",!0),A.ar("^/",!0)))
s($,"ID","nq",()=>new A.lH(A.ar("[/\\\\]",!0),A.ar("[^/\\\\]$",!0),A.ar("^(\\\\\\\\[^\\\\]+\\\\[^\\\\/]+|[a-zA-Z]:[/\\\\])",!0),A.ar("^[/\\\\](?![/\\\\])",!0)))
s($,"IC","iZ",()=>new A.lE(A.ar("/",!0),A.ar("(^[a-zA-Z][-+.a-zA-Z\\d]*://|[^/])$",!0),A.ar("[a-zA-Z][-+.a-zA-Z\\d]*://[^/]*",!0),A.ar("^/",!0)))
s($,"IA","zu",()=>A.F0())
s($,"J1","zx",()=>A.a([A.aN("Overview","overview",A.HA()),A.aN("Performance","performance",A.Ht()),A.aN("Memory","memory",A.HF()),A.aN("Entities","entities",A.Hv()),A.aN("Chunks","chunks",A.HD()),A.aN("Mechanics","mechanics",A.Hy()),A.aN("Events","events",A.HS()),A.aN("Internals","internals",A.Hz()),A.aN("Incidents","incidents",A.HR()),A.aN("Worlds","worlds",A.HC()),A.aN("World Overrides","world-overrides",A.HM()),A.aN("Integrations","integrations",A.HH()),A.aN("Heatmaps","heatmaps",A.HQ()),A.aN("Optimization","optimization",A.HI()),A.aN("Tweaks","tweaks",A.HP()),A.aN("Governors","governors",A.HN()),A.aN("Actions","actions",A.HG()),A.aN("Incident Center","incident-center",A.HO()),A.aN("Environment","environment",A.HK()),A.aN("Config Editor","config",A.Hw()),A.aN("Logs","logs",A.HJ())],A.aJ("D<mt>")))
s($,"Iv","CT",()=>A.ar("RCT1\\.[A-Za-z0-9_-]+={0,2}",!0))
s($,"J0","Dc",()=>A.ar("\xa7.",!0))})();(function nativeSupport(){!function(){var s=function(a){var m={}
m[a]=1
return Object.keys(hunkHelpers.convertToFastObject(m))[0]}
v.getIsolateTag=function(a){return s("___dart_"+a+v.isolateTag)}
var r="___dart_isolate_tags_"
var q=Object[r]||(Object[r]=Object.create(null))
var p="_ZxYxX"
for(var o=0;;o++){var n=s(p+"_"+o+"_")
if(!(n in q)){q[n]=1
v.isolateTag=n
break}}v.dispatchPropertyName=v.getIsolateTag("dispatch_record")}()
hunkHelpers.setOrUpdateInterceptorsByTag({ArrayBuffer:A.eY,SharedArrayBuffer:A.eY,ArrayBufferView:A.hz,DataView:A.kl,Float32Array:A.km,Float64Array:A.kn,Int16Array:A.ko,Int32Array:A.kp,Int8Array:A.kq,Uint16Array:A.ks,Uint32Array:A.hA,Uint8ClampedArray:A.hB,CanvasPixelArray:A.hB,Uint8Array:A.dT})
hunkHelpers.setOrUpdateLeafTags({ArrayBuffer:true,SharedArrayBuffer:true,ArrayBufferView:false,DataView:true,Float32Array:true,Float64Array:true,Int16Array:true,Int32Array:true,Int8Array:true,Uint16Array:true,Uint32Array:true,Uint8ClampedArray:true,CanvasPixelArray:true,Uint8Array:false})
A.b_.$nativeSuperclassTag="ArrayBufferView"
A.io.$nativeSuperclassTag="ArrayBufferView"
A.ip.$nativeSuperclassTag="ArrayBufferView"
A.hy.$nativeSuperclassTag="ArrayBufferView"
A.iq.$nativeSuperclassTag="ArrayBufferView"
A.ir.$nativeSuperclassTag="ArrayBufferView"
A.bA.$nativeSuperclassTag="ArrayBufferView"})()
Function.prototype.$0=function(){return this()}
Function.prototype.$1=function(a){return this(a)}
Function.prototype.$2=function(a,b){return this(a,b)}
Function.prototype.$3=function(a,b,c){return this(a,b,c)}
Function.prototype.$4=function(a,b,c,d){return this(a,b,c,d)}
Function.prototype.$1$0=function(){return this()}
Function.prototype.$1$1=function(a){return this(a)}
Function.prototype.$2$1=function(a){return this(a)}
convertAllToFastObject(w)
convertToFastObject($);(function(a){if(typeof document==="undefined"){a(null)
return}if(typeof document.currentScript!="undefined"){a(document.currentScript)
return}var s=document.scripts
function onLoad(b){for(var q=0;q<s.length;++q){s[q].removeEventListener("load",onLoad,false)}a(b.target)}for(var r=0;r<s.length;++r){s[r].addEventListener("load",onLoad,false)}})(function(a){v.currentScript=a
var s=A.I3
if(typeof dartMainRunner==="function"){dartMainRunner(s,[])}else{s([])}})})()